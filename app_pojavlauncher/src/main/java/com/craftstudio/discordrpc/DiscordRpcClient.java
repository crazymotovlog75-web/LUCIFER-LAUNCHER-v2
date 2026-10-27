package com.craftstudio.discordrpc;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

/**
 * Pushes launcher presence to the Discord bridge from a background daemon
 * thread. Safe to call from the UI thread: every setter returns immediately.
 *
 * <p>Only the newest presence is kept — bursts of updates collapse into one
 * HTTP request, and failures retry with exponential backoff (2s → 30s) without
 * ever blocking the caller or spinning.
 *
 * <pre>
 * DiscordRpcClient rpc = DiscordRpcClient.create(DiscordRpcConfig.builder()
 *         .endpoint("http://192.168.1.42:6464")
 *         .clientId("123456789012345678")
 *         .build());
 * rpc.setIdle();                                     // launcher menu
 * rpc.setPlaying("1.21.10", "Fabric", "fabric-loader-0.17.3-1.21.10");
 * rpc.clear();                                       // stop showing anything
 * </pre>
 */
public final class DiscordRpcClient implements Closeable {

    /** Called on the worker thread whenever the connection state changes. */
    public interface StateListener {
        void onStateChanged(State state, String message);
    }

    public enum State {
        /** Nothing queued, nothing shown. */
        IDLE,
        /** Request queued / in flight. */
        SYNCING,
        /** Bridge accepted the presence. */
        ACTIVE,
        /** Request failed, will retry. */
        RETRYING,
        /** Disabled by the user or closed. */
        DISABLED
    }

    private final DiscordRpcConfig config;
    private final String sinceLaunch;

    private final Object lock = new Object();
    private RichPresence pending;
    private boolean pendingClear;
    private volatile boolean stopped;
    private volatile boolean enabled;
    private volatile State state = State.IDLE;
    private volatile String lastMessage = "";

    private Thread worker;
    private StateListener listener;

    private DiscordRpcClient(DiscordRpcConfig config) {
        this.config = config;
        this.enabled = config.isEnabled();
        this.sinceLaunch = String.valueOf(System.currentTimeMillis());
    }

    /**
     * Creates the client and starts its worker thread. Never throws for network
     * problems — a bridge that is offline is simply retried.
     */
    public static DiscordRpcClient create(DiscordRpcConfig config) {
        DiscordRpcClient client = new DiscordRpcClient(config);
        client.start();
        return client;
    }

    public DiscordRpcConfig getConfig() {
        return config;
    }

    public State getState() {
        return state;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public boolean isEnabled() {
        return enabled && !stopped;
    }

    public void setStateListener(StateListener listener) {
        this.listener = listener;
    }

    /** User toggle. Disabling clears the presence and stops all traffic. */
    public void setEnabled(boolean on) {
        this.enabled = on;
        if (!on) {
            setState(State.DISABLED, "disabled by user");
            clear();
        }
    }

    // ── Public presence API ────────────────────────────────────────────────

    /** Launcher is open but no game is running. */
    public void setIdle() {
        setPresence(LauncherPresence.idle());
    }

    /**
     * A Minecraft session is running. Starts the elapsed timer now unless a
     * start timestamp is supplied.
     */
    public void setPlaying(String mcVersion, String modLoader, String profileName) {
        setPresence(LauncherPresence.playing(mcVersion, modLoader, profileName, System.currentTimeMillis()));
    }

    public void setPlaying(String mcVersion, String modLoader, String profileName, long startTimestampMillis) {
        setPresence(LauncherPresence.playing(mcVersion, modLoader, profileName, startTimestampMillis));
    }

    /** Replaces the card with an arbitrary activity. */
    public void setPresence(RichPresence presence) {
        if (presence == null) {
            clear();
            return;
        }
        synchronized (lock) {
            pending = presence;
            pendingClear = false;
            lock.notifyAll();
        }
    }

    /** Removes the presence card entirely. */
    public void clear() {
        synchronized (lock) {
            pending = null;
            pendingClear = true;
            lock.notifyAll();
        }
    }

    /** Blocks until the current request settles. Call off the UI thread only. */
    public boolean flush(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            State s = state;
            if (s == State.ACTIVE || s == State.IDLE || stopped || !enabled) return true;
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    // ── Worker ─────────────────────────────────────────────────────────────

    private void start() {
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "discord-rpc-client");
        worker.setDaemon(true);
        worker.start();
    }

    private void loop() {
        // hasRequest: the current activity survived a failed attempt and must be retried
        boolean hasRequest = false;
        boolean doClear = false;
        RichPresence activity = null;
        long backoff = 2000;

        while (!stopped) {
            if (!hasRequest) {
                synchronized (lock) {
                    while (!stopped && pending == null && !pendingClear) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    if (stopped) return;
                    activity = pending;
                    doClear = pendingClear;
                    pending = null;
                    pendingClear = false;
                    hasRequest = true;
                }
            }

            if (!enabled) {
                hasRequest = false;
                setState(State.DISABLED, "disabled by user");
                continue;
            }

            setState(State.SYNCING, doClear ? "clearing presence" : String.valueOf(activity));
            try {
                int code = doClear ? delete() : post(activity);
                if (code < 200 || code >= 300) {
                    throw new IOException("bridge returned HTTP " + code);
                }
                hasRequest = false;
                backoff = 2000;
                setState(doClear ? State.IDLE : State.ACTIVE,
                        doClear ? "presence cleared" : "presence active");
            } catch (IOException e) {
                setState(State.RETRYING, e.getClass().getSimpleName() + ": " + e.getMessage());
                synchronized (lock) {
                    if (pending != null || pendingClear) {
                        // a newer request supersedes the failed one
                        hasRequest = false;
                    } else {
                        try {
                            lock.wait(backoff);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
                backoff = Math.min(backoff * 2, 30000);
            }
        }
    }

    private int post(RichPresence presence) throws IOException {
        String body = Json.presencePayload(config.getClientId(), presence);
        return request("POST", body);
    }

    private int delete() throws IOException {
        return request("DELETE", null);
    }

    private int request(String method, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) config.getEndpoint()
                .resolve("/presence")
                .toURL()
                .openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(config.getConnectTimeoutMs());
            conn.setReadTimeout(config.getReadTimeoutMs());
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", "LuciferLauncher-DiscordRPC/1.0");
            conn.setRequestProperty("X-Bridge-Session", sinceLaunch);
            if (!config.getToken().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + config.getToken());
            }
            if (body != null) {
                byte[] bytes = body.getBytes("UTF-8");
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(bytes.length);
                OutputStream os = conn.getOutputStream();
                try {
                    os.write(bytes);
                } finally {
                    os.close();
                }
            }
            int code = conn.getResponseCode();
            drain(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            return code;
        } finally {
            conn.disconnect();
        }
    }

    private static void drain(InputStream in) {
        if (in == null) return;
        try {
            byte[] buf = new byte[512];
            while (in.read(buf) != -1) {
                // discard
            }
        } catch (IOException ignored) {
            // body content is irrelevant for the caller
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void setState(State s, String message) {
        this.state = s;
        this.lastMessage = message == null ? "" : message;
        StateListener l = listener;
        if (l != null) {
            try {
                l.onStateChanged(s, lastMessage);
            } catch (RuntimeException ignored) {
                // a broken listener must not kill the worker
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            stopped = true;
            lock.notifyAll();
        }
        setState(State.DISABLED, "closed");
        if (worker != null) {
            try {
                worker.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

package net.kdt.pojavlaunch.discord;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.craftstudio.discordrpc.DiscordDiscovery;
import com.craftstudio.discordrpc.DiscordRpcClient;
import com.craftstudio.discordrpc.DiscordRpcConfig;
import com.craftstudio.discordrpc.LauncherPresence;
import com.craftstudio.discordrpc.RichPresence;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin Android glue between the launcher and the Discord presence SDK.
 *
 * <p>Everything is static and null-safe on purpose: hook sites can call
 * {@code DiscordRpcManager.idle()} from anywhere without caring whether the
 * feature is enabled, configured or even initialised.
 *
 * <p>Preferences (Settings → Miscellaneous):
 * <ul>
 *     <li>{@code discordRpc} — master switch (off by default)</li>
 *     <li>{@code discordRpcEndpoint} — bridge address, e.g. {@code http://192.168.1.42:6464}</li>
 *     <li>{@code discordRpcToken} — must match {@code bridge-token.txt} on the PC</li>
 * </ul>
 *
 * <p>The Discord application id is not configurable here: it is baked into
 * {@link DiscordRpcConfig#LUCIFER_APPLICATION_ID}.
 */
public final class DiscordRpcManager {

    private static final String TAG = "DiscordRpc";

    public static final String PREF_ENABLED = "discordRpc";
    public static final String PREF_ENDPOINT = "discordRpcEndpoint";
    public static final String PREF_TOKEN = "discordRpcToken";
    /** Leave the address empty (or "auto") and the PC is found over UDP. */
    public static final String ENDPOINT_AUTO = "auto";
    public static final String DEFAULT_ENDPOINT = ENDPOINT_AUTO;

    private static final Object LOCK = new Object();
    private static DiscordRpcManager sInstance;
    /** Discovery must never block the UI thread, so it runs here. */
    private static final ExecutorService sResolver = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "discord-rpc-discovery");
        t.setDaemon(true);
        return t;
    });

    private final Context appContext;
    private DiscordRpcClient client;
    /** enabled|endpoint|token — rebuild the client only when this changes. */
    private String signature = "";
    private long sessionStartMillis;

    private DiscordRpcManager(Context appContext) {
        this.appContext = appContext;
    }

    // ── Lifecycle (called from PojavApplication / activities) ──────────────

    /** Call once from {@code PojavApplication.onCreate()}. Safe to call twice. */
    public static void onAppStart(Context context) {
        if (context == null) return;
        synchronized (LOCK) {
            if (sInstance == null) {
                sInstance = new DiscordRpcManager(context.getApplicationContext());
            }
        }
        sInstance.applyPreferences();
    }

    /** Re-read settings (called after the settings screen saves). */
    public static void refreshConfig() {
        DiscordRpcManager m = sInstance;
        if (m != null) m.applyPreferences();
    }

    /** Launcher menu is visible / game exited. */
    public static void idle() {
        DiscordRpcManager m = sInstance;
        if (m != null) m.setIdleInternal();
    }

    /** A Minecraft session started. {@code versionId} is the profile/instance id. */
    public static void gameLaunch(String versionId) {
        DiscordRpcManager m = sInstance;
        if (m != null) m.setPlayingInternal(versionId);
    }

    /** The JVM returned (crash dialog, exit, etc.). */
    public static void gameExit() {
        DiscordRpcManager m = sInstance;
        if (m != null) m.setIdleInternal();
    }

    /** App is being torn down for real. */
    public static void shutdown() {
        synchronized (LOCK) {
            if (sInstance != null) {
                sInstance.closeClient();
                sInstance = null;
            }
        }
    }

    /** Fires a sample "playing" card so users can verify the bridge quickly. */
    public static void sendTestPresence() {
        DiscordRpcManager m = sInstance;
        if (m == null || m.client == null) {
            Log.w(TAG, "test presence skipped: Discord RPC is off or not configured");
            return;
        }
        m.client.setPlaying("1.21.10", "Fabric", "fabric-loader-0.17.3-1.21.10");
    }

    /** Human readable state for settings tooltips / debugging. */
    public static String describeState() {
        DiscordRpcManager m = sInstance;
        if (m == null) return "not initialised";
        if (m.client == null) return "disabled / searching";
        return m.client.getState() + " (" + m.client.getLastMessage() + ")";
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private void applyPreferences() {
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        if (prefs == null) {
            prefs = appContext.getSharedPreferences("cslauncher_settings", Context.MODE_PRIVATE);
        }
        boolean enabled = prefs.getBoolean(PREF_ENABLED, false);
        String endpoint = prefs.getString(PREF_ENDPOINT, DEFAULT_ENDPOINT);
        String token = prefs.getString(PREF_TOKEN, "");
        if (endpoint == null || endpoint.trim().isEmpty()) endpoint = DEFAULT_ENDPOINT;
        endpoint = endpoint.trim();
        if (token == null) token = "";

        String newSignature = enabled + "|" + endpoint + "|" + token;
        if (newSignature.equals(signature)) {
            if (client != null) client.setEnabled(enabled);
            return;
        }
        signature = newSignature;

        closeClient();
        if (!enabled) {
            Log.i(TAG, "disabled");
            return;
        }

        boolean auto = endpoint.isEmpty() || ENDPOINT_AUTO.equalsIgnoreCase(endpoint);
        if (auto) {
            resolveAndStart(token);
        } else {
            start(endpoint, token);
        }
    }

    /** Runs LAN discovery off the main thread, then connects. */
    private void resolveAndStart(final String token) {
        final String expected = signature;
        sResolver.execute(() -> {
            DiscordDiscovery.Bridge bridge = DiscordDiscovery.discover();
            if (!expected.equals(signature)) {
                Log.i(TAG, "discovery result ignored (settings changed)");
                return;
            }
            if (bridge == null) {
                Log.w(TAG, "no bridge found on the LAN — start start-bridge.bat on your PC");
                return;
            }
            Log.i(TAG, "bridge found: " + bridge + " (matched app id " + bridge.appId + ")");
            start(bridge.toEndpoint().toString(), token);
        });
    }

    private void start(String endpoint, String token) {
        try {
            DiscordRpcConfig config = DiscordRpcConfig.builder()
                    .endpoint(URI.create(endpoint))
                    .token(token)
                    .enabled(true)
                    .build();
            client = DiscordRpcClient.create(config);
            client.setStateListener((state, message) -> Log.d(TAG, state + ": " + message));
            Log.i(TAG, "started against " + endpoint);
            setIdleInternal();
        } catch (RuntimeException e) {
            // bad endpoint in settings must never crash the launcher
            Log.w(TAG, "bad configuration: " + e.getMessage());
            client = null;
        }
    }

    private void setIdleInternal() {
        DiscordRpcClient c = client;
        if (c == null) return;
        sessionStartMillis = 0;
        c.setPresence(LauncherPresence.idle());
    }

    private void setPlayingInternal(String versionId) {
        DiscordRpcClient c = client;
        if (c == null) return;
        if (versionId == null) versionId = "";
        String mcVersion = LauncherPresence.detectMinecraftVersion(versionId);
        String loader = LauncherPresence.detectLoader(versionId);
        if (sessionStartMillis == 0) sessionStartMillis = System.currentTimeMillis();
        c.setPlaying(mcVersion, loader, versionId, sessionStartMillis);
    }

    private void closeClient() {
        DiscordRpcClient c = client;
        client = null;
        if (c != null) c.close();
    }

    /** Optional: custom card for special launcher states (installing, updating…). */
    public static void custom(RichPresence presence) {
        DiscordRpcManager m = sInstance;
        if (m != null && m.client != null) m.client.setPresence(presence);
    }
}

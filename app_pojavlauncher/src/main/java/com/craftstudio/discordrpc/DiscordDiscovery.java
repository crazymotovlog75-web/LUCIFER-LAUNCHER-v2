package com.craftstudio.discordrpc;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.Charset;

/**
 * Finds a LUCIFER presence bridge on the local network, so the user never has
 * to type the PC's IP address.
 *
 * <p>The bridge answers a UDP broadcast on {@link #DEFAULT_DISCOVERY_PORT}:
 *
 * <pre>
 *   "LUCIFER_BRIDGE_DISCOVER"  →
 *   {"app":"lucifer-bridge","port":6464,"host":"DESKTOP-1","appId":"...","needsToken":false}
 * </pre>
 *
 * <p>Blocking call (~1.5 s worst case) — never run it on the UI thread.
 */
public final class DiscordDiscovery {

    public static final String PROBE = "LUCIFER_BRIDGE_DISCOVER";
    public static final int DEFAULT_DISCOVERY_PORT = 6465;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** What a bridge told us about itself. */
    public static final class Bridge {
        public final String hostAddress;
        public final int httpPort;
        public final String hostname;
        public final String appId;
        public final boolean needsToken;

        Bridge(String hostAddress, int httpPort, String hostname, String appId, boolean needsToken) {
            this.hostAddress = hostAddress;
            this.httpPort = httpPort;
            this.hostname = hostname;
            this.appId = appId;
            this.needsToken = needsToken;
        }

        /** Endpoint to hand to {@link DiscordRpcConfig.Builder#endpoint(String)}. */
        public URI toEndpoint() {
            return URI.create("http://" + hostAddress + ":" + httpPort);
        }

        @Override
        public String toString() {
            return "Bridge{" + hostname + " @ " + hostAddress + ":" + httpPort + '}';
        }
    }

    private DiscordDiscovery() {
    }

    /**
     * Broadcasts a probe and returns the first bridge that answers.
     *
     * @return the bridge, or {@code null} if none responded within the timeout
     */
    public static Bridge discover(int discoveryPort, int timeoutMs) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(Math.max(200, timeoutMs));

            byte[] probe = PROBE.getBytes(UTF8);
            DatagramPacket out = new DatagramPacket(
                    probe, probe.length, InetAddress.getByName("255.255.255.255"), discoveryPort);
            socket.send(out);

            byte[] buf = new byte[1024];
            long deadline = System.currentTimeMillis() + Math.max(200, timeoutMs);
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket in = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(in);
                } catch (SocketTimeoutException e) {
                    return null;
                }
                String text = new String(in.getData(), 0, in.getLength(), UTF8);
                Bridge bridge = parse(text, in.getSocketAddress());
                if (bridge != null) return bridge;
            }
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            if (socket != null) socket.close();
        }
    }

    public static Bridge discover() {
        return discover(DEFAULT_DISCOVERY_PORT, 1500);
    }

    /** Parses a bridge reply; returns null when it is not one. */
    static Bridge parse(String json, java.net.SocketAddress from) {
        if (json == null || !json.contains("lucifer-bridge")) return null;
        int port = intField(json, "port", -1);
        if (port <= 0 || port > 65535) return null;
        String host = stringField(json, "host");
        String appId = stringField(json, "appId");
        String address = null;
        if (from instanceof InetSocketAddress) {
            InetAddress a = ((InetSocketAddress) from).getAddress();
            if (a != null) address = a.getHostAddress();
        }
        if (address == null) return null;
        return new Bridge(address, port, host, appId, json.contains("\"needsToken\":true"));
    }

    private static int intField(String json, String name, int fallback) {
        String key = "\"" + name + "\":";
        int i = json.indexOf(key);
        if (i < 0) return fallback;
        int start = i + key.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String stringField(String json, String name) {
        String key = "\"" + name + "\":\"";
        int i = json.indexOf(key);
        if (i < 0) return "";
        int start = i + key.length();
        int end = json.indexOf('"', start);
        return end < 0 ? "" : json.substring(start, end);
    }
}

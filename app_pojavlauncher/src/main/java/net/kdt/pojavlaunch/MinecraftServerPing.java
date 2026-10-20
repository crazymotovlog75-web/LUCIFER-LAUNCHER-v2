package net.kdt.pojavlaunch;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.IDN;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Minimal Minecraft Java Edition Server List Ping implementation. All network calls are blocking;
 * callers must run {@link #ping(String)} away from the UI thread. */
public final class MinecraftServerPing {
    private static final int DEFAULT_PORT = 25565;
    private static final int CONNECT_TIMEOUT_MS = 2800;
    private static final int READ_TIMEOUT_MS = 2800;
    private static final int OPTIONAL_PING_TIMEOUT_MS = 1200;
    private static final int MAX_STATUS_PACKET_BYTES = 1_048_576;
    private static final int MAX_FAVICON_BYTES = 64 * 1024;
    private static final int MAX_MOTD_CHARS = 512;

    private MinecraftServerPing() {}

    public static final class Endpoint {
        public final String host;
        public final int port;

        Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String toAddress() {
            String printableHost = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
            return port == DEFAULT_PORT ? printableHost : printableHost + ":" + port;
        }

        /** Stable, case-insensitive host/port key used to reject accidental duplicate entries. */
        public String normalizedKey() {
            return host.toLowerCase(java.util.Locale.ROOT) + ":" + port;
        }
    }

    public static final class PingResult {
        public final long latencyMs;
        public final int onlinePlayers;
        public final int maximumPlayers;
        public final String versionName;
        /** Plain Minecraft-formatted text, not HTML. */
        public final String motd;
        /** Optional, validated PNG bytes supplied by the server status response. */
        public final byte[] faviconPng;

        PingResult(long latencyMs, int onlinePlayers, int maximumPlayers, String versionName,
                   String motd, byte[] faviconPng) {
            this.latencyMs = latencyMs;
            this.onlinePlayers = onlinePlayers;
            this.maximumPlayers = maximumPlayers;
            this.versionName = versionName;
            this.motd = motd;
            this.faviconPng = faviconPng;
        }
    }

    public static Endpoint parseEndpoint(String input) {
        if (input == null) throw new IllegalArgumentException("Enter a server address");
        String value = input.trim();
        if (value.isEmpty() || value.length() > 255 || value.contains(" ") ||
                value.contains("\n") || value.contains("\r") || value.contains("\t")) {
            throw new IllegalArgumentException("Enter a valid hostname or IP address");
        }

        String host;
        int port = DEFAULT_PORT;
        if (value.startsWith("[")) {
            int closing = value.indexOf(']');
            if (closing < 2) throw new IllegalArgumentException("Invalid IPv6 address");
            host = value.substring(1, closing);
            String suffix = value.substring(closing + 1);
            if (!suffix.isEmpty()) {
                if (!suffix.startsWith(":")) throw new IllegalArgumentException("Invalid server port");
                port = parsePort(suffix.substring(1));
            }
            if (!host.contains(":") || !host.matches("(?i)[0-9a-f:.%]+")) {
                throw new IllegalArgumentException("Invalid IPv6 address");
            }
        } else {
            int firstColon = value.indexOf(':');
            int lastColon = value.lastIndexOf(':');
            if (firstColon >= 0 && firstColon == lastColon) {
                host = value.substring(0, firstColon);
                port = parsePort(value.substring(firstColon + 1));
            } else {
                // Unbracketed IPv6 is accepted with the default port; use [address]:port otherwise.
                host = value;
            }
            if (host.matches("(?i)[0-9a-f:.%]+") && host.contains(":")) {
                // Raw IPv6 literal.
            } else {
                try {
                    host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid hostname", e);
                }
                if (host.length() > 253 || !host.matches("(?i)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")) {
                    throw new IllegalArgumentException("Enter a valid hostname or IP address");
                }
                for (String label : host.split("\\.", -1)) {
                    if (label.isEmpty() || label.length() > 63 ||
                            !label.matches("(?i)[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) {
                        throw new IllegalArgumentException("Enter a valid hostname or IP address");
                    }
                }
            }
        }

        if (host.isEmpty()) throw new IllegalArgumentException("Enter a hostname");
        return new Endpoint(host, port);
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
    }

    public static PingResult ping(String address) throws IOException, JSONException {
        Endpoint endpoint = parseEndpoint(address);
        try (Socket socket = new Socket()) {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            DataInputStream input = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
            DataOutputStream handshake = new DataOutputStream(handshakeBytes);
            writeVarInt(handshake, 0);                 // Handshake packet id
            writeVarInt(handshake, 760);               // Status protocol; status is version agnostic
            writeString(handshake, endpoint.host);
            handshake.writeShort(endpoint.port);
            writeVarInt(handshake, 1);                 // Next state: status
            byte[] handshakePacket = handshakeBytes.toByteArray();
            writeVarInt(output, handshakePacket.length);
            output.write(handshakePacket);

            writeVarInt(output, 1);                    // Request packet length
            writeVarInt(output, 0);                    // Request packet id
            output.flush();

            int packetLength = readVarInt(input);
            if (packetLength <= 0 || packetLength > MAX_STATUS_PACKET_BYTES) {
                throw new IOException("Invalid status packet length");
            }
            int packetId = readVarInt(input);
            if (packetId != 0) throw new IOException("Unexpected server status response");
            int jsonLength = readVarInt(input);
            if (jsonLength < 0 || jsonLength > packetLength) throw new IOException("Invalid status response");
            byte[] jsonBytes = new byte[jsonLength];
            input.readFully(jsonBytes);

            JSONObject status = new JSONObject(new String(jsonBytes, StandardCharsets.UTF_8));
            JSONObject players = status.optJSONObject("players");
            JSONObject version = status.optJSONObject("version");
            String versionName = version != null ? version.optString("name", "") : "";
            String motd = flattenDescription(status.opt("description"));
            byte[] favicon = decodeFavicon(status.optString("favicon", ""));

            // A valid status JSON response already proves the server is online. Measure latency
            // with the optional ping/pong packet when supported, but do not mislabel a server as
            // offline merely because a proxy closes the socket or omits that optional reply.
            long latency = -1L;
            try {
                socket.setSoTimeout(OPTIONAL_PING_TIMEOUT_MS);
                long pingToken = android.os.SystemClock.elapsedRealtime();
                writeVarInt(output, 9); // packet id (1) + eight-byte payload
                writeVarInt(output, 1); // Ping packet id
                output.writeLong(pingToken);
                output.flush();
                int pongLength = readVarInt(input);
                if (pongLength != 9 || readVarInt(input) != 1 || input.readLong() != pingToken) {
                    throw new IOException("Unexpected server ping response");
                }
                latency = Math.max(0L, android.os.SystemClock.elapsedRealtime() - pingToken);
            } catch (IOException pingUnavailable) {
                android.util.Log.d("MinecraftServerPing",
                        "Status response received; round-trip ping unavailable: " +
                                pingUnavailable.getClass().getSimpleName());
            }
            return new PingResult(
                    latency,
                    players != null ? players.optInt("online", -1) : -1,
                    players != null ? players.optInt("max", -1) : -1,
                    versionName,
                    motd,
                    favicon);
        }
    }

    private static String flattenDescription(Object value) {
        StringBuilder out = new StringBuilder();
        appendDescription(value, out, 0);
        if (out.length() > MAX_MOTD_CHARS) out.setLength(MAX_MOTD_CHARS);
        return out.toString().trim();
    }

    private static void appendDescription(Object value, StringBuilder out, int depth) {
        if (value == null || depth > 12 || out.length() >= MAX_MOTD_CHARS) return;
        if (value instanceof String) {
            String text = (String) value;
            int remaining = MAX_MOTD_CHARS - out.length();
            if (remaining > 0) out.append(text, 0, Math.min(text.length(), remaining));
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
            return;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length() && out.length() < MAX_MOTD_CHARS; i++) {
                appendDescription(array.opt(i), out, depth + 1);
            }
            return;
        }
        if (!(value instanceof JSONObject)) return;

        JSONObject component = (JSONObject) value;
        String colorValue = component.optString("color", "");
        String colorCode = colorCodeForName(colorValue);
        if (colorCode != null) out.append('\u00a7').append(colorCode);
        else if (colorValue.matches("(?i)#[0-9a-f]{6}")) {
            out.append("\u00a7x");
            for (int i = 1; i < colorValue.length(); i++) out.append('\u00a7').append(colorValue.charAt(i));
        }
        if (component.optBoolean("bold", false)) out.append("\u00a7l");
        if (component.optBoolean("italic", false)) out.append("\u00a7o");
        if (component.optBoolean("underlined", false)) out.append("\u00a7n");
        if (component.optBoolean("strikethrough", false)) out.append("\u00a7m");

        Object text = component.opt("text");
        if (text != null) appendDescription(text, out, depth + 1);
        Object extra = component.opt("extra");
        if (extra != null) appendDescription(extra, out, depth + 1);
    }

    private static String colorCodeForName(String color) {
        switch (color.toLowerCase(java.util.Locale.ROOT)) {
            case "black": return "0";
            case "dark_blue": return "1";
            case "dark_green": return "2";
            case "dark_aqua": return "3";
            case "dark_red": return "4";
            case "dark_purple": return "5";
            case "gold": return "6";
            case "gray": return "7";
            case "dark_gray": return "8";
            case "blue": return "9";
            case "green": return "a";
            case "aqua": return "b";
            case "red": return "c";
            case "light_purple": return "d";
            case "yellow": return "e";
            case "white": return "f";
            default: return null;
        }
    }

    private static byte[] decodeFavicon(String dataUri) {
        final String prefix = "data:image/png;base64,";
        if (dataUri == null || !dataUri.startsWith(prefix) || dataUri.length() > MAX_FAVICON_BYTES * 2) {
            return null;
        }
        try {
            byte[] png = Base64.decode(dataUri.substring(prefix.length()), Base64.DEFAULT);
            if (png.length < 8 || png.length > MAX_FAVICON_BYTES ||
                    (png[0] & 0xff) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
                return null;
            }
            return png;
        } catch (IllegalArgumentException invalidBase64) {
            return null;
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(output, bytes.length);
        output.write(bytes);
    }

    private static void writeVarInt(DataOutputStream output, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            output.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.writeByte(value);
    }

    private static int readVarInt(DataInputStream input) throws IOException {
        int value = 0;
        int position = 0;
        byte current;
        do {
            current = input.readByte();
            value |= (current & 0x7F) << position;
            position += 7;
            if (position > 35) throw new IOException("VarInt is too long");
        } while ((current & 0x80) != 0);
        return value;
    }
}

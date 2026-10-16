package net.kdt.pojavlaunch;

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
    private static final int MAX_STATUS_PACKET_BYTES = 1_048_576;

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
    }

    public static final class PingResult {
        public final long latencyMs;
        public final int onlinePlayers;
        public final int maximumPlayers;
        public final String versionName;

        PingResult(long latencyMs, int onlinePlayers, int maximumPlayers, String versionName) {
            this.latencyMs = latencyMs;
            this.onlinePlayers = onlinePlayers;
            this.maximumPlayers = maximumPlayers;
            this.versionName = versionName;
        }
    }

    public static Endpoint parseEndpoint(String input) {
        if (input == null) throw new IllegalArgumentException("Enter a server address");
        String value = input.trim();
        if (value.isEmpty() || value.length() > 255 || value.contains(" ") ||
                value.contains("\n") || value.contains("\r")) {
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
                // raw IPv6 literal
            } else {
                try {
                    host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid hostname", e);
                }
                if (host.length() > 253 || !host.matches("(?i)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")) {
                    throw new IllegalArgumentException("Enter a valid hostname or IP address");
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
        long startedAt = android.os.SystemClock.elapsedRealtime();
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
            long latency = Math.max(0L, android.os.SystemClock.elapsedRealtime() - startedAt);

            JSONObject status = new JSONObject(new String(jsonBytes, StandardCharsets.UTF_8));
            JSONObject players = status.optJSONObject("players");
            JSONObject version = status.optJSONObject("version");
            return new PingResult(
                    latency,
                    players != null ? players.optInt("online", -1) : -1,
                    players != null ? players.optInt("max", -1) : -1,
                    version != null ? version.optString("name", "") : "");
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

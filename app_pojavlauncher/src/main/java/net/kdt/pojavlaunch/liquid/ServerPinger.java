package net.kdt.pojavlaunch.liquid;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Minimal Minecraft Server List Ping (1.7+ / "modern" protocol) client.
 *
 * <p>Used by the Server Hub to show a real online/offline state, player counts and latency instead of
 * a hardcoded decoration. Runs on a background thread and always returns a result object — it never
 * throws at the caller.</p>
 */
public final class ServerPinger {

    private ServerPinger() {
    }

    public static class Status {
        public boolean online;
        public String version = "";
        public String motd = "";
        public int players;
        public int maxPlayers;
        public long latencyMs = -1L;

        @NonNull
        @Override
        public String toString() {
            return online ? (players + "/" + maxPlayers + " · " + latencyMs + "ms") : "offline";
        }
    }

    /** Pings {@code host:port} with the modern SLP handshake. Never throws. */
    public static Status ping(String host, int port, int timeoutMs) {
        Status status = new Status();
        if (host == null || host.isEmpty()) return status;

        Socket socket = new Socket();
        long start = System.currentTimeMillis();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            // handshake
            ByteArrayOutputStream hsBuf = new ByteArrayOutputStream();
            DataOutputStream hs = new DataOutputStream(hsBuf);
            writeVarInt(hs, 0x00);          // packet id
            writeVarInt(hs, -1);            // protocol version (-1 = "just asking")
            writeVarInt(hs, host.length());
            hs.writeBytes(host);            // ASCII host
            hs.writeShort(port);
            writeVarInt(hs, 1);             // next state: status
            writeVarInt(out, hsBuf.size());
            out.write(hsBuf.toByteArray());

            // status request
            writeVarInt(out, 1);
            writeVarInt(out, 0x00);
            out.flush();

            status.latencyMs = System.currentTimeMillis() - start;

            DataInputStream in = new DataInputStream(socket.getInputStream());
            int packetLength = readVarInt(in);
            if (packetLength <= 0) return status;
            int packetId = readVarInt(in);
            if (packetId != 0x00) return status;
            int jsonLength = readVarInt(in);
            if (jsonLength <= 0 || jsonLength > 8 * 1024 * 1024) return status;
            byte[] payload = new byte[jsonLength];
            in.readFully(payload);

            JSONObject json = new JSONObject(new String(payload, "UTF-8"));
            status.version = json.optJSONObject("version") != null
                    ? json.optJSONObject("version").optString("name", "") : "";
            status.motd = flattenChat(json.opt("description"));
            JSONObject players = json.optJSONObject("players");
            if (players != null) {
                status.players = players.optInt("online", 0);
                status.maxPlayers = players.optInt("max", 0);
            }
            status.online = true;
        } catch (Throwable t) {
            status.online = false;
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        return status;
    }

    /** MOTD may be a plain string or a chat component tree — flatten it to text. */
    private static String flattenChat(Object description) {
        if (description == null) return "";
        if (description instanceof String) return (String) description;
        StringBuilder sb = new StringBuilder();
        JSONObject obj = (JSONObject) description;
        sb.append(obj.optString("text", ""));
        org.json.JSONArray extra = obj.optJSONArray("extra");
        if (extra != null) {
            for (int i = 0; i < extra.length(); i++) {
                Object child = extra.opt(i);
                if (child instanceof JSONObject) {
                    sb.append(((JSONObject) child).optString("text", ""));
                } else if (child != null) {
                    sb.append(child.toString());
                }
            }
        }
        return sb.toString().trim();
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        int v = value;
        do {
            int temp = v & 0x7F;
            v >>>= 7;
            if (v != 0) temp |= 0x80;
            out.writeByte(temp);
        } while (v != 0);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = (read & 0x7F);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) throw new IOException("VarInt too big");
        } while ((read & 0x80) != 0);
        return result;
    }
}

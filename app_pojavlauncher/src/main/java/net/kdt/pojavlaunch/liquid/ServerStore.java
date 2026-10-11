package net.kdt.pojavlaunch.liquid;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Persists the Server Hub list and writes it into the selected profile's {@code servers.dat}
 * (NBT, gzip) so the very same servers also appear in Minecraft's own multiplayer screen.
 */
public final class ServerStore {

    private static final String PREFS = "lc_server_hub";
    private static final String KEY_LIST = "servers";

    public static class Entry {
        public String name;
        public String address;
        public boolean featured;
        public int port = 25565;

        public Entry(String name, String address) {
            this(name, address, false);
        }

        public Entry(String name, String address, boolean featured) {
            this.name = name;
            this.address = address;
            this.featured = featured;
        }

        /** Splits "host:port" and returns the host part. */
        public String host() {
            String a = address == null ? "" : address.trim();
            int idx = a.indexOf(':');
            return idx > 0 ? a.substring(0, idx) : a;
        }

        public int port() {
            String a = address == null ? "" : address.trim();
            int idx = a.indexOf(':');
            if (idx > 0) {
                try {
                    return Integer.parseInt(a.substring(idx + 1).trim());
                } catch (NumberFormatException ignored) {
                }
            }
            return 25565;
        }
    }

    private ServerStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The default list: the community servers plus the launcher's own network. */
    public static List<Entry> defaults() {
        List<Entry> list = new ArrayList<>();
        list.add(new Entry("CS Network", "play.csnetwork.fun", true));
        list.add(new Entry("licifermc", "play.lucifermc.fun", false));
        list.add(new Entry("BananaSMP", "bananasmp.net", false));
        list.add(new Entry("AscendiaMC", "fast.ascendiamc.com", false));
        list.add(new Entry("HappyMC", "play.happymc.fun", false));
        list.add(new Entry("InSane SMP", "insanesmp.net", false));
        list.add(new Entry("EternalNetwork", "fast.eternalnetwork.club", false));
        return list;
    }

    public static List<Entry> load(Context context) {
        String raw = prefs(context).getString(KEY_LIST, null);
        if (raw == null) return defaults();
        List<Entry> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "");
                String address = o.optString("address", "");
                if (name.isEmpty() || address.isEmpty()) continue;
                list.add(new Entry(name, address, o.optBoolean("featured", false)));
            }
        } catch (Throwable t) {
            return defaults();
        }
        if (list.isEmpty()) return defaults();
        return list;
    }

    public static void save(Context context, @NonNull List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry e : entries) {
            JSONObject o = new JSONObject();
            try {
                o.put("name", e.name);
                o.put("address", e.address);
                o.put("featured", e.featured);
            } catch (Throwable ignored) {
            }
            array.put(o);
        }
        prefs(context).edit().putString(KEY_LIST, array.toString()).apply();
    }

    public static void add(Context context, String name, String address) {
        List<Entry> list = load(context);
        list.add(new Entry(name, address, false));
        save(context, list);
    }

    public static void remove(Context context, Entry entry) {
        List<Entry> list = load(context);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).address.equalsIgnoreCase(entry.address)) {
                list.remove(i);
                break;
            }
        }
        save(context, list);
    }

    /**
     * Writes the list into {@code <profile dir>/servers.dat} in vanilla NBT form.
     *
     * @return true when the file was written
     */
    public static boolean writeServersDat(File gameDir, List<Entry> entries) {
        if (gameDir == null) return false;
        File target = new File(gameDir, "servers.dat");
        DataOutputStream out = null;
        try {
            //noinspection ResultOfMethodCallIgnored
            gameDir.mkdirs();
            out = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(new FileOutputStream(target))));

            out.writeByte(10);          // TAG_Compound (root)
            out.writeUTF("");           // root name

            out.writeByte(9);           // TAG_List
            out.writeUTF("servers");
            out.writeByte(10);          // of TAG_Compound
            out.writeInt(entries.size());
            for (Entry e : entries) {
                out.writeByte(10);      // TAG_Compound
                out.writeUTF("");       // element name

                out.writeByte(8);       // TAG_String
                out.writeUTF("name");
                out.writeUTF(e.name);

                out.writeByte(8);       // TAG_String
                out.writeUTF("ip");
                out.writeUTF(e.address);

                out.writeByte(1);       // TAG_Byte
                out.writeUTF("acceptTextures");
                out.writeByte(0);

                out.writeByte(0);       // TAG_End (entry)
            }
            out.writeByte(0);           // TAG_End (root)
            out.flush();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}

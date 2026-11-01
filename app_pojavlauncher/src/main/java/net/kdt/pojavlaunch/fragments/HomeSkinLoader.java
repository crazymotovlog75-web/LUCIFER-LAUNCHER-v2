package net.kdt.pojavlaunch.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Fetches an official Minecraft skin without blocking the UI thread. */
final class HomeSkinLoader {
    static final class Result {
        final Bitmap skin;
        final boolean slim;
        Result(Bitmap skin, boolean slim) { this.skin = skin; this.slim = slim; }
    }

    static Result load(String uuid) {
        try {
            String id = uuid.replace("-", "");
            if (!id.matches("[0-9a-fA-F]{32}")) return null;
            JSONObject profile = new JSONObject(new String(read("https://sessionserver.mojang.com/session/minecraft/profile/" + id, 65536), StandardCharsets.UTF_8));
            JSONArray properties = profile.optJSONArray("properties");
            if (properties == null) return null;
            for (int i = 0; i < properties.length(); i++) {
                JSONObject property = properties.optJSONObject(i);
                if (property == null || !"textures".equals(property.optString("name"))) continue;
                String decoded = new String(Base64.decode(property.getString("value"), Base64.DEFAULT), StandardCharsets.UTF_8);
                JSONObject skinInfo = new JSONObject(decoded).getJSONObject("textures").optJSONObject("SKIN");
                if (skinInfo == null) return null;
                URL texture = new URL(skinInfo.getString("url"));
                if (!"textures.minecraft.net".equalsIgnoreCase(texture.getHost()) || !texture.getPath().matches("/texture/[0-9a-fA-F]+")) return null;
                // The profile may contain an http URL; always use TLS and a known host.
                byte[] bytes = read("https://textures.minecraft.net" + texture.getPath(), 1024 * 1024);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null || bitmap.getWidth() != 64 || (bitmap.getHeight() != 64 && bitmap.getHeight() != 32)) return null;
                JSONObject metadata = skinInfo.optJSONObject("metadata");
                return new Result(bitmap, metadata != null && "slim".equals(metadata.optString("model")));
            }
        } catch (Exception ignored) {
            // Offline/unavailable accounts continue to use the default skin.
        }
        return null;
    }

    private static byte[] read(String address, int limit) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200) throw new java.io.IOException("Skin request failed");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > limit) throw new java.io.IOException("Skin response too large");
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }
}

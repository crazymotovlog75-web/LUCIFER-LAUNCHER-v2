package net.kdt.pojavlaunch;

import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps a one-shot multiplayer destination for direct server launches. */
public final class QuickPlayManager {
    private static final String PREF_ADDRESS = "lucifer_pending_quickplay_address";
    private static final String PREF_CREATED_AT = "lucifer_pending_quickplay_created_at";
    private static final long REQUEST_TTL_MS = 10 * 60 * 1000L;
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?:^|[^0-9])1\\.(\\d+)\\.(\\d+)(?:$|[^0-9])");

    private QuickPlayManager() {}

    public static boolean supportsQuickPlay(@Nullable String versionId) {
        if (TextUtils.isEmpty(versionId)) return false;
        Matcher matcher = VERSION_PATTERN.matcher(versionId);
        int majorLine = -1;
        int patch = -1;
        while (matcher.find()) {
            try {
                majorLine = Integer.parseInt(matcher.group(1));
                patch = Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException ignored) {
                majorLine = -1;
            }
        }
        if (majorLine < 0) return false;
        return majorLine > 20 || (majorLine == 20 && patch >= 5);
    }

    /** Add the version-appropriate command-line arguments for a one-shot server connection. */
    public static void appendLaunchArguments(List<String> arguments,
                                             @Nullable JMinecraftVersionList.Version version,
                                             @Nullable String address) {
        if (arguments == null || !isSafeAddress(address)) return;
        if (supportsQuickPlay(version)) {
            arguments.add("--quickPlayMultiplayer");
            arguments.add(address.trim());
            return;
        }
        try {
            MinecraftServerPing.Endpoint endpoint = MinecraftServerPing.parseEndpoint(address);
            arguments.add("--server");
            arguments.add(endpoint.host);
            arguments.add("--port");
            arguments.add(Integer.toString(endpoint.port));
        } catch (IllegalArgumentException ignored) {
            // Invalid pending values must never prevent a normal game launch.
        }
    }

    private static boolean supportsQuickPlay(@Nullable JMinecraftVersionList.Version version) {
        if (version == null) return false;
        return supportsQuickPlay(version.inheritsFrom) || supportsQuickPlay(version.id);
    }

    public static boolean request(@Nullable String address) {
        SharedPreferences preferences = LauncherPreferences.DEFAULT_PREF;
        if (preferences == null || !isSafeAddress(address)) return false;
        final String normalizedAddress;
        try {
            normalizedAddress = MinecraftServerPing.parseEndpoint(address).toAddress();
        } catch (IllegalArgumentException invalidAddress) {
            return false;
        }
        preferences.edit()
                .putString(PREF_ADDRESS, normalizedAddress)
                .putLong(PREF_CREATED_AT, System.currentTimeMillis())
                .apply();
        return true;
    }

    /** Consume and clear the one-shot address so a normal later launch never inherits it. */
    @Nullable
    public static String consumePendingAddress() {
        SharedPreferences preferences = LauncherPreferences.DEFAULT_PREF;
        if (preferences == null) return null;
        String address = preferences.getString(PREF_ADDRESS, null);
        long createdAt = preferences.getLong(PREF_CREATED_AT, 0L);
        preferences.edit().remove(PREF_ADDRESS).remove(PREF_CREATED_AT).apply();
        long ageMs = System.currentTimeMillis() - createdAt;
        if (!isSafeAddress(address) || createdAt <= 0L || ageMs < 0L ||
                ageMs > REQUEST_TTL_MS) {
            return null;
        }
        return address.trim();
    }

    private static boolean isSafeAddress(@Nullable String address) {
        if (TextUtils.isEmpty(address)) return false;
        String value = address.trim();
        return value.length() <= 255 && !value.isEmpty() &&
                value.indexOf(' ') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
    }

}

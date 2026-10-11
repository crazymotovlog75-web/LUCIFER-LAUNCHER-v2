package net.kdt.pojavlaunch;

import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps a one-shot multiplayer destination for modern Minecraft Quick Play launches. */
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
    public static String consumePendingAddress(@Nullable JMinecraftVersionList.Version version) {
        SharedPreferences preferences = LauncherPreferences.DEFAULT_PREF;
        if (preferences == null) return null;
        String address = preferences.getString(PREF_ADDRESS, null);
        long createdAt = preferences.getLong(PREF_CREATED_AT, 0L);
        preferences.edit().remove(PREF_ADDRESS).remove(PREF_CREATED_AT).apply();
        long ageMs = System.currentTimeMillis() - createdAt;
        if (!isSafeAddress(address) || createdAt <= 0L || ageMs < 0L ||
                ageMs > REQUEST_TTL_MS || !supportsQuickPlay(version)) {
            return null;
        }
        return address.trim();
    }

    private static boolean supportsQuickPlay(@Nullable JMinecraftVersionList.Version version) {
        if (version == null) return false;
        return supportsQuickPlay(version.inheritsFrom) || supportsQuickPlay(version.id);
    }

    private static boolean isSafeAddress(@Nullable String address) {
        if (TextUtils.isEmpty(address)) return false;
        String value = address.trim();
        return value.length() <= 255 && !value.isEmpty() &&
                value.indexOf(' ') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
    }

}

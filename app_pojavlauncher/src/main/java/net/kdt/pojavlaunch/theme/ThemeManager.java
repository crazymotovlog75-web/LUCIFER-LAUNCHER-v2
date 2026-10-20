package net.kdt.pojavlaunch.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.palette.graphics.Palette;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.fragments.RightPaneHomeFragment;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;

public class ThemeManager {

    private static final String KEY_THEME = "launcher_theme";
    private static final String KEY_WALLPAPER_ID = "lucifer_wallpaper_id";
    public static final String KEY_GRADIENT = "enable_bg_gradient";

    public static final Preset[] PRESETS = {
        new Preset("Default (Lucifer Launcher)",  R.style.AppTheme,            R.style.AppTheme_Gradient),
        new Preset("Midnight Blue",     R.style.AppTheme_MidnightBlue, R.style.AppTheme_MidnightBlue_Gradient),
        new Preset("Forest Green",      R.style.AppTheme_ForestGreen,  R.style.AppTheme_ForestGreen_Gradient),
        new Preset("Crimson",           R.style.AppTheme_Crimson,      R.style.AppTheme_Crimson_Gradient),
        new Preset("Amethyst",          R.style.AppTheme_Amethyst,     R.style.AppTheme_Amethyst_Gradient),
        new Preset("Arctic",            R.style.AppTheme_Arctic,       R.style.AppTheme_Arctic_Gradient),
    };

    /** The initial Lucifer look is the first curated scene; old explicit presets remain honored. */
    public static int getSelectedWallpaperId() {
        android.content.SharedPreferences preferences = LauncherPreferences.DEFAULT_PREF;
        if (preferences.contains(KEY_WALLPAPER_ID)) {
            int selected = preferences.getInt(KEY_WALLPAPER_ID, -1);
            return WallpaperCatalog.get(selected) != null ? selected : -1;
        }
        int savedTheme = preferences.getInt(KEY_THEME, R.style.AppTheme);
        boolean oldGradient = preferences.getBoolean(KEY_GRADIENT, false);
        if (savedTheme != R.style.AppTheme || oldGradient) return -1;
        return WallpaperCatalog.DEFAULT_ID;
    }

    public static void selectWallpaper(int wallpaperId) {
        if (WallpaperCatalog.get(wallpaperId) == null) return;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putInt(KEY_WALLPAPER_ID, wallpaperId)
                .putInt(KEY_THEME, R.style.AppTheme)
                .apply();
    }

    public static void applyPreset(@NonNull Preset preset) {
        if (preset.styleRes == R.style.AppTheme) {
            selectWallpaper(WallpaperCatalog.DEFAULT_ID);
            return;
        }
        LauncherPreferences.DEFAULT_PREF.edit()
                .putInt(KEY_WALLPAPER_ID, -1)
                .putInt(KEY_THEME, preset.styleRes)
                .apply();
    }

    public static void resetToDefault() {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putInt(KEY_WALLPAPER_ID, WallpaperCatalog.DEFAULT_ID)
                .putInt(KEY_THEME, R.style.AppTheme)
                .apply();
    }

    /** Resolves a palette role from the Activity theme, with a fallback for legacy screens. */
    @ColorInt
    public static int resolveColor(@NonNull Context context, @AttrRes int attribute, @ColorInt int fallback) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, value, true)) return fallback;
        if (value.resourceId != 0) {
            try {
                return androidx.core.content.ContextCompat.getColor(context, value.resourceId);
            } catch (RuntimeException ignored) {}
        }
        return value.data;
    }

    @ColorInt
    public static int getAccentColor(@NonNull Context context) {
        return resolveColor(context, R.attr.luciferAccent, 0xFF7BE7E4);
    }

    @ColorInt
    public static int getSecondaryColor(@NonNull Context context) {
        return resolveColor(context, R.attr.luciferSecondary, 0xFFB8A0FF);
    }

    /**
     * Apply the current theme's bgMainDrawable to a preference fragment's root view.
     * Called from LauncherPreferenceFragment.onViewCreated().
     */
    public static void applyToPrefView(@NonNull android.view.View view) {
        android.util.TypedValue tv = new android.util.TypedValue();
        view.getContext().getTheme().resolveAttribute(
                net.kdt.pojavlaunch.R.attr.bgMainDrawable, tv, true);
        if (tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                && tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
            view.setBackgroundColor(tv.data);
        } else if (tv.resourceId != 0) {
            view.setBackgroundResource(tv.resourceId);
        }
    }

    /**
     * Call in Activity.onCreate() BEFORE setContentView().
     * Returns the flat or gradient style depending on the gradient toggle.
     */
    @StyleRes
    public static int getSavedTheme() {
        int selectedWallpaper = getSelectedWallpaperId();
        WallpaperCatalog.Wallpaper wallpaper = WallpaperCatalog.get(selectedWallpaper);
        boolean gradient = LauncherPreferences.DEFAULT_PREF.getBoolean(KEY_GRADIENT, false);
        if (wallpaper != null) return gradient ? wallpaper.gradientThemeRes : wallpaper.themeRes;

        int base = LauncherPreferences.DEFAULT_PREF.getInt(KEY_THEME, R.style.AppTheme);

        // Normalise: always find the matching flat preset style
        // (guards against an old gradient style ID being stored in prefs)
        Preset matched = PRESETS[0];
        for (Preset p : PRESETS) {
            if (p.styleRes == base || p.gradientStyleRes == base) {
                matched = p;
                break;
            }
        }

        return gradient ? matched.gradientStyleRes : matched.styleRes;
    }

    /**
     * Use Palette API to pick the closest built-in preset from the custom background.
     */
    public static boolean applyFromCustomBackground() {
        File bgFile = new File(RightPaneHomeFragment.CUSTOM_BG_PATH);
        if (!bgFile.exists()) return false;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 4;
        Bitmap bmp = BitmapFactory.decodeFile(bgFile.getAbsolutePath(), opts);
        if (bmp == null) return false;

        Palette palette = Palette.from(bmp).maximumColorCount(24).generate();
        bmp.recycle();

        Palette.Swatch dominant = firstNonNull(
            palette.getDarkVibrantSwatch(),
            palette.getVibrantSwatch(),
            palette.getDarkMutedSwatch(),
            palette.getMutedSwatch()
        );
        if (dominant == null) return false;

        float[] hsl = dominant.getHsl();
        float[] presetHues = { 20f, 210f, 120f, 0f, 280f, 185f };
        Preset best = PRESETS[0];
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < PRESETS.length; i++) {
            float dist = Math.abs(hsl[0] - presetHues[i]);
            if (dist > 180) dist = 360 - dist;
            if (dist < bestDist) { bestDist = dist; best = PRESETS[i]; }
        }

        applyPreset(best);
        return true;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... items) {
        for (T t : items) if (t != null) return t;
        return null;
    }

    public static final class Preset {
        public final String name;
        public final int styleRes;
        public final int gradientStyleRes;
        public Preset(String name, int styleRes, int gradientStyleRes) {
            this.name             = name;
            this.styleRes         = styleRes;
            this.gradientStyleRes = gradientStyleRes;
        }
    }
}
package net.kdt.pojavlaunch.theme;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StyleRes;

import net.kdt.pojavlaunch.R;

/** Curated bundled wallpaper and matching adaptive palette catalog. */
public final class WallpaperCatalog {
    public static final int DEFAULT_ID = 0;

    public static final Wallpaper[] ALL = {
        new Wallpaper(0, R.drawable.lucifer_wallpaper_01, R.drawable.lucifer_wallpaper_preview_01, R.style.AppTheme_Wallpaper01, R.style.AppTheme_Wallpaper01_Gradient, R.color.wallpaper_01_accent, R.color.wallpaper_01_secondary, R.color.wallpaper_01_surface, R.color.wallpaper_01_border),
        new Wallpaper(1, R.drawable.lucifer_wallpaper_02, R.drawable.lucifer_wallpaper_preview_02, R.style.AppTheme_Wallpaper02, R.style.AppTheme_Wallpaper02_Gradient, R.color.wallpaper_02_accent, R.color.wallpaper_02_secondary, R.color.wallpaper_02_surface, R.color.wallpaper_02_border),
        new Wallpaper(2, R.drawable.lucifer_wallpaper_03, R.drawable.lucifer_wallpaper_preview_03, R.style.AppTheme_Wallpaper03, R.style.AppTheme_Wallpaper03_Gradient, R.color.wallpaper_03_accent, R.color.wallpaper_03_secondary, R.color.wallpaper_03_surface, R.color.wallpaper_03_border),
        new Wallpaper(3, R.drawable.lucifer_wallpaper_04, R.drawable.lucifer_wallpaper_preview_04, R.style.AppTheme_Wallpaper04, R.style.AppTheme_Wallpaper04_Gradient, R.color.wallpaper_04_accent, R.color.wallpaper_04_secondary, R.color.wallpaper_04_surface, R.color.wallpaper_04_border),
        new Wallpaper(4, R.drawable.lucifer_wallpaper_05, R.drawable.lucifer_wallpaper_preview_05, R.style.AppTheme_Wallpaper05, R.style.AppTheme_Wallpaper05_Gradient, R.color.wallpaper_05_accent, R.color.wallpaper_05_secondary, R.color.wallpaper_05_surface, R.color.wallpaper_05_border),
        new Wallpaper(5, R.drawable.lucifer_wallpaper_06, R.drawable.lucifer_wallpaper_preview_06, R.style.AppTheme_Wallpaper06, R.style.AppTheme_Wallpaper06_Gradient, R.color.wallpaper_06_accent, R.color.wallpaper_06_secondary, R.color.wallpaper_06_surface, R.color.wallpaper_06_border),
        new Wallpaper(6, R.drawable.lucifer_wallpaper_07, R.drawable.lucifer_wallpaper_preview_07, R.style.AppTheme_Wallpaper07, R.style.AppTheme_Wallpaper07_Gradient, R.color.wallpaper_07_accent, R.color.wallpaper_07_secondary, R.color.wallpaper_07_surface, R.color.wallpaper_07_border),
        new Wallpaper(7, R.drawable.lucifer_wallpaper_08, R.drawable.lucifer_wallpaper_preview_08, R.style.AppTheme_Wallpaper08, R.style.AppTheme_Wallpaper08_Gradient, R.color.wallpaper_08_accent, R.color.wallpaper_08_secondary, R.color.wallpaper_08_surface, R.color.wallpaper_08_border),
        new Wallpaper(8, R.drawable.lucifer_wallpaper_09, R.drawable.lucifer_wallpaper_preview_09, R.style.AppTheme_Wallpaper09, R.style.AppTheme_Wallpaper09_Gradient, R.color.wallpaper_09_accent, R.color.wallpaper_09_secondary, R.color.wallpaper_09_surface, R.color.wallpaper_09_border),
        new Wallpaper(9, R.drawable.lucifer_wallpaper_10, R.drawable.lucifer_wallpaper_preview_10, R.style.AppTheme_Wallpaper10, R.style.AppTheme_Wallpaper10_Gradient, R.color.wallpaper_10_accent, R.color.wallpaper_10_secondary, R.color.wallpaper_10_surface, R.color.wallpaper_10_border),
        new Wallpaper(10, R.drawable.lucifer_wallpaper_11, R.drawable.lucifer_wallpaper_preview_11, R.style.AppTheme_Wallpaper11, R.style.AppTheme_Wallpaper11_Gradient, R.color.wallpaper_11_accent, R.color.wallpaper_11_secondary, R.color.wallpaper_11_surface, R.color.wallpaper_11_border),
        new Wallpaper(11, R.drawable.lucifer_wallpaper_12, R.drawable.lucifer_wallpaper_preview_12, R.style.AppTheme_Wallpaper12, R.style.AppTheme_Wallpaper12_Gradient, R.color.wallpaper_12_accent, R.color.wallpaper_12_secondary, R.color.wallpaper_12_surface, R.color.wallpaper_12_border),
        new Wallpaper(12, R.drawable.lucifer_wallpaper_13, R.drawable.lucifer_wallpaper_preview_13, R.style.AppTheme_Wallpaper13, R.style.AppTheme_Wallpaper13_Gradient, R.color.wallpaper_13_accent, R.color.wallpaper_13_secondary, R.color.wallpaper_13_surface, R.color.wallpaper_13_border),
        new Wallpaper(13, R.drawable.lucifer_wallpaper_14, R.drawable.lucifer_wallpaper_preview_14, R.style.AppTheme_Wallpaper14, R.style.AppTheme_Wallpaper14_Gradient, R.color.wallpaper_14_accent, R.color.wallpaper_14_secondary, R.color.wallpaper_14_surface, R.color.wallpaper_14_border),
        new Wallpaper(14, R.drawable.lucifer_wallpaper_15, R.drawable.lucifer_wallpaper_preview_15, R.style.AppTheme_Wallpaper15, R.style.AppTheme_Wallpaper15_Gradient, R.color.wallpaper_15_accent, R.color.wallpaper_15_secondary, R.color.wallpaper_15_surface, R.color.wallpaper_15_border),
        new Wallpaper(15, R.drawable.lucifer_wallpaper_16, R.drawable.lucifer_wallpaper_preview_16, R.style.AppTheme_Wallpaper16, R.style.AppTheme_Wallpaper16_Gradient, R.color.wallpaper_16_accent, R.color.wallpaper_16_secondary, R.color.wallpaper_16_surface, R.color.wallpaper_16_border),
        new Wallpaper(16, R.drawable.lucifer_wallpaper_17, R.drawable.lucifer_wallpaper_preview_17, R.style.AppTheme_Wallpaper17, R.style.AppTheme_Wallpaper17_Gradient, R.color.wallpaper_17_accent, R.color.wallpaper_17_secondary, R.color.wallpaper_17_surface, R.color.wallpaper_17_border),
        new Wallpaper(17, R.drawable.lucifer_wallpaper_18, R.drawable.lucifer_wallpaper_preview_18, R.style.AppTheme_Wallpaper18, R.style.AppTheme_Wallpaper18_Gradient, R.color.wallpaper_18_accent, R.color.wallpaper_18_secondary, R.color.wallpaper_18_surface, R.color.wallpaper_18_border),
        new Wallpaper(18, R.drawable.lucifer_wallpaper_19, R.drawable.lucifer_wallpaper_preview_19, R.style.AppTheme_Wallpaper19, R.style.AppTheme_Wallpaper19_Gradient, R.color.wallpaper_19_accent, R.color.wallpaper_19_secondary, R.color.wallpaper_19_surface, R.color.wallpaper_19_border),
        new Wallpaper(19, R.drawable.lucifer_wallpaper_20, R.drawable.lucifer_wallpaper_preview_20, R.style.AppTheme_Wallpaper20, R.style.AppTheme_Wallpaper20_Gradient, R.color.wallpaper_20_accent, R.color.wallpaper_20_secondary, R.color.wallpaper_20_surface, R.color.wallpaper_20_border),
        new Wallpaper(20, R.drawable.lucifer_wallpaper_black, R.drawable.lucifer_wallpaper_black, R.style.AppTheme_Wallpaper21, R.style.AppTheme_Wallpaper21_Gradient, R.color.wallpaper_21_accent, R.color.wallpaper_21_secondary, R.color.wallpaper_21_surface, R.color.wallpaper_21_border)
    };

    private WallpaperCatalog() {}

    public static Wallpaper get(int id) {
        return id >= 0 && id < ALL.length ? ALL[id] : null;
    }

    public static final class Wallpaper {
        public final int id;
        @DrawableRes public final int drawableRes;
        @DrawableRes public final int previewRes;
        @StyleRes public final int themeRes;
        @StyleRes public final int gradientThemeRes;
        @ColorRes public final int accentColorRes;
        @ColorRes public final int secondaryColorRes;
        @ColorRes public final int surfaceColorRes;
        @ColorRes public final int borderColorRes;

        private Wallpaper(int id, int drawableRes, int previewRes, int themeRes, int gradientThemeRes,
                int accentColorRes, int secondaryColorRes, int surfaceColorRes, int borderColorRes) {
            this.id = id;
            this.drawableRes = drawableRes;
            this.previewRes = previewRes;
            this.themeRes = themeRes;
            this.gradientThemeRes = gradientThemeRes;
            this.accentColorRes = accentColorRes;
            this.secondaryColorRes = secondaryColorRes;
            this.surfaceColorRes = surfaceColorRes;
            this.borderColorRes = borderColorRes;
        }
    }
}

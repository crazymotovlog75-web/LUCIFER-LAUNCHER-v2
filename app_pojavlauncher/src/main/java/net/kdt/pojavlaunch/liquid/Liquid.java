package net.kdt.pojavlaunch.liquid;

import android.animation.ValueAnimator;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * Small helpers shared by the Liquid Crystal screens.
 *
 * <p>Everything here animates only GPU-composited properties (scale / alpha / translation) and
 * never allocates inside a draw pass, so the motion stays smooth on low-end devices.</p>
 */
public final class Liquid {

    private Liquid() {
    }

    /** True when the platform still wants animations (respects "remove animations" / battery saver). */
    public static boolean animationsEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                return ValueAnimator.areAnimatorsEnabled();
            } catch (Throwable ignored) {
                return true;
            }
        }
        return true;
    }

    /** Adds a springy press feedback: scale down on touch, back up on release. No layout passes. */
    public static void pressFeedback(@Nullable View... views) {
        if (views == null) return;
        for (final View v : views) {
            if (v == null) continue;
            v.setOnTouchListener((view, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().cancel();
                        view.animate().scaleX(0.94f).scaleY(0.94f)
                                .setDuration(70)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().cancel();
                        view.animate().scaleX(1f).scaleY(1f)
                                .setDuration(140)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                        break;
                }
                return false;
            });
        }
    }

    /** Reveals a view with a soft rise + fade. Used for sheets and cards appearing on screen. */
    public static void reveal(@Nullable View view, long delayMs) {
        if (view == null) return;
        if (!animationsEnabled()) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(view.getResources().getDisplayMetrics().density * 14f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(320)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /**
     * Switches a dock/toolbar orb between its idle glass look and its lit "liquid core" state.
     *
     * @param orb    the container (gets the ring background)
     * @param core   the liquid core view behind the icon (alpha animated)
     * @param active whether this orb is the current destination
     */
    public static void setOrbActive(@Nullable View orb, @Nullable View core, boolean active) {
        if (core != null) {
            if (animationsEnabled()) {
                core.animate()
                        .alpha(active ? 1f : 0f)
                        .scaleX(active ? 1f : 0.6f)
                        .scaleY(active ? 1f : 0.6f)
                        .setDuration(220)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else {
                core.setAlpha(active ? 1f : 0f);
                core.setScaleX(active ? 1f : 0.6f);
                core.setScaleY(active ? 1f : 0.6f);
            }
        }
        if (orb != null) {
            orb.setSelected(active);
        }
    }
}

package net.kdt.pojavlaunch.utils;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

/** Renders the legacy formatting codes used by Minecraft server MOTDs as Android text spans. */
public final class MinecraftTextFormatter {
    private static final int[] LEGACY_COLORS = {
            Color.rgb(0, 0, 0), Color.rgb(0, 0, 170), Color.rgb(0, 170, 0), Color.rgb(0, 170, 170),
            Color.rgb(170, 0, 0), Color.rgb(170, 0, 170), Color.rgb(255, 170, 0), Color.rgb(170, 170, 170),
            Color.rgb(85, 85, 85), Color.rgb(85, 85, 255), Color.rgb(85, 255, 85), Color.rgb(85, 255, 255),
            Color.rgb(255, 85, 85), Color.rgb(255, 85, 255), Color.rgb(255, 255, 85), Color.rgb(255, 255, 255)
    };

    private MinecraftTextFormatter() {}

    public static CharSequence format(String input) {
        if (input == null || input.isEmpty()) return "MOTD unavailable";
        SpannableStringBuilder output = new SpannableStringBuilder();
        State state = new State();
        int runStart = 0;
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) != '\u00a7' || i + 1 >= input.length()) {
                output.append(input.charAt(i));
                continue;
            }

            char code = Character.toLowerCase(input.charAt(i + 1));
            Integer hexColor = readHexColor(input, i);
            if (hexColor != null) {
                apply(output, runStart, output.length(), state);
                state.reset();
                state.color = hexColor;
                i += 13;
                runStart = output.length();
                continue;
            }

            int colorIndex = Character.digit(code, 16);
            boolean recognizedColor = colorIndex >= 0 && colorIndex < LEGACY_COLORS.length;
            boolean recognizedFormat = code == 'l' || code == 'o' || code == 'n' || code == 'm' || code == 'r';
            if (!recognizedColor && !recognizedFormat) {
                output.append(input.charAt(i));
                continue;
            }

            apply(output, runStart, output.length(), state);
            if (recognizedColor) {
                state.reset();
                state.color = LEGACY_COLORS[colorIndex];
            } else if (code == 'r') {
                state.reset();
            } else if (code == 'l') {
                state.bold = true;
            } else if (code == 'o') {
                state.italic = true;
            } else if (code == 'n') {
                state.underline = true;
            } else if (code == 'm') {
                state.strikethrough = true;
            }
            i++;
            runStart = output.length();
        }
        apply(output, runStart, output.length(), state);
        return output;
    }

    private static Integer readHexColor(String input, int start) {
        // Modern form: §x§R§R§G§G§B§B
        if (start + 13 >= input.length() || Character.toLowerCase(input.charAt(start + 1)) != 'x') return null;
        StringBuilder hex = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            int sectionIndex = start + 2 + i * 2;
            if (input.charAt(sectionIndex) != '\u00a7') return null;
            char digit = input.charAt(sectionIndex + 1);
            if (Character.digit(digit, 16) < 0) return null;
            hex.append(digit);
        }
        try {
            return Color.parseColor("#" + hex);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void apply(SpannableStringBuilder text, int start, int end, State state) {
        if (start >= end) return;
        int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        if (state.color != null) text.setSpan(new ForegroundColorSpan(state.color), start, end, flags);
        if (state.bold || state.italic) {
            int style = state.bold && state.italic ? Typeface.BOLD_ITALIC
                    : state.bold ? Typeface.BOLD : Typeface.ITALIC;
            text.setSpan(new StyleSpan(style), start, end, flags);
        }
        if (state.underline) text.setSpan(new UnderlineSpan(), start, end, flags);
        if (state.strikethrough) text.setSpan(new StrikethroughSpan(), start, end, flags);
    }

    private static final class State {
        Integer color;
        boolean bold;
        boolean italic;
        boolean underline;
        boolean strikethrough;

        void reset() {
            color = null;
            bold = false;
            italic = false;
            underline = false;
            strikethrough = false;
        }
    }
}

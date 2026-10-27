package com.craftstudio.discordrpc;

import java.util.List;

/**
 * Tiny JSON writer — keeps the SDK dependency-free (no Gson/Jackson on the
 * launcher classpath) while producing exactly what the bridge expects.
 */
final class Json {

    private Json() {
    }

    static String presencePayload(String clientId, RichPresence presence) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        field(sb, "clientId", clientId).append(',');
        sb.append("\"activity\":");
        activity(sb, presence);
        sb.append('}');
        return sb.toString();
    }

    static String activityJson(RichPresence p) {
        StringBuilder sb = new StringBuilder(256);
        activity(sb, p);
        return sb.toString();
    }

    private static void activity(StringBuilder sb, RichPresence p) {
        sb.append('{');
        boolean first = true;
        if (p.getDetails() != null) {
            first = field(sb, "details", p.getDetails(), first);
        }
        if (p.getState() != null) {
            first = field(sb, "state", p.getState(), first);
        }
        // type: 0 = "Playing ..."
        if (!first) sb.append(',');
        sb.append("\"type\":").append(p.getType());
        first = false;
        if (p.getStartTimestampMillis() > 0) {
            sb.append(',')
                    .append("\"timestamps\":{\"start\":")
                    .append(p.getStartTimestampMillis())
                    .append('}');
        }
        if (p.getLargeImageKey() != null || p.getSmallImageKey() != null) {
            sb.append(',').append("\"assets\":{");
            boolean a = true;
            if (p.getLargeImageKey() != null) {
                a = field(sb, "large_image", p.getLargeImageKey(), a);
            }
            if (p.getLargeImageText() != null) {
                a = field(sb, "large_text", p.getLargeImageText(), a);
            }
            if (p.getSmallImageKey() != null) {
                a = field(sb, "small_image", p.getSmallImageKey(), a);
            }
            if (p.getSmallImageText() != null) {
                field(sb, "small_text", p.getSmallImageText(), a);
            }
            sb.append('}');
        }
        List<String[]> buttons = p.getButtons();
        if (!buttons.isEmpty()) {
            sb.append(',').append("\"buttons\":[");
            for (int i = 0; i < buttons.size(); i++) {
                if (i > 0) sb.append(',');
                String[] b = buttons.get(i);
                sb.append('{');
                field(sb, "label", b[0], true);
                sb.append(',');
                field(sb, "url", b[1], true);
                sb.append('}');
            }
            sb.append(']');
        }
        sb.append(',').append("\"instance\":false");
        sb.append('}');
    }

    private static StringBuilder field(StringBuilder sb, String name, String value) {
        sb.append('"').append(name).append("\":");
        quote(sb, value);
        return sb;
    }

    private static boolean field(StringBuilder sb, String name, String value, boolean first) {
        if (!first) sb.append(',');
        field(sb, name, value);
        return false;
    }

    static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}

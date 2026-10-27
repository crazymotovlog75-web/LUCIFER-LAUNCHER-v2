package com.craftstudio.discordrpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable Discord activity ("Rich Presence" card).
 *
 * Values map 1:1 to the Discord RPC SET_ACTIVITY payload:
 * {@code details} is the top line, {@code state} the second line,
 * {@code startTimestampMillis} drives the elapsed timer (0:41 in the card).
 */
public final class RichPresence {

    /** Discord activity types. */
    public static final int TYPE_PLAYING = 0;
    public static final int TYPE_LISTENING = 1;
    public static final int TYPE_WATCHING = 2;
    public static final int TYPE_COMPETING = 3;

    private final int type;
    private final String details;
    private final String state;
    private final long startTimestampMillis;
    private final String largeImageKey;
    private final String largeImageText;
    private final String smallImageKey;
    private final String smallImageText;
    private final List<String[]> buttons;

    private RichPresence(Builder b) {
        this.type = b.type;
        this.details = b.details;
        this.state = b.state;
        this.startTimestampMillis = b.startTimestampMillis;
        this.largeImageKey = b.largeImageKey;
        this.largeImageText = b.largeImageText;
        this.smallImageKey = b.smallImageKey;
        this.smallImageText = b.smallImageText;
        this.buttons = Collections.unmodifiableList(new ArrayList<>(b.buttons));
    }

    public int getType() {
        return type;
    }

    public String getDetails() {
        return details;
    }

    public String getState() {
        return state;
    }

    public long getStartTimestampMillis() {
        return startTimestampMillis;
    }

    public String getLargeImageKey() {
        return largeImageKey;
    }

    public String getLargeImageText() {
        return largeImageText;
    }

    public String getSmallImageKey() {
        return smallImageKey;
    }

    public String getSmallImageText() {
        return smallImageText;
    }

    /** Each entry is {@code [label, url]}. Discord allows at most two. */
    public List<String[]> getButtons() {
        return buttons;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Convenience copy with a different second line. */
    public RichPresence withState(String newState) {
        return toBuilder().state(newState).build();
    }

    public Builder toBuilder() {
        return new Builder()
                .type(type)
                .details(details)
                .state(state)
                .startTimestamp(startTimestampMillis)
                .largeImage(largeImageKey, largeImageText)
                .smallImage(smallImageKey, smallImageText);
    }

    public static final class Builder {
        private int type = TYPE_PLAYING;
        private String details;
        private String state;
        private long startTimestampMillis;
        private String largeImageKey;
        private String largeImageText;
        private String smallImageKey;
        private String smallImageText;
        private final List<String[]> buttons = new ArrayList<>(2);

        public Builder type(int type) {
            this.type = type;
            return this;
        }

        /** Top line, max 128 chars — e.g. "Playing Minecraft 1.21.10". */
        public Builder details(String details) {
            this.details = truncate(details, 128);
            return this;
        }

        /** Second line, max 128 chars — e.g. "Fabric 1.21.10". */
        public Builder state(String state) {
            this.state = truncate(state, 128);
            return this;
        }

        /** Elapsed timer start. Use 0 (default) for no timer. */
        public Builder startTimestamp(long epochMillis) {
            this.startTimestampMillis = epochMillis;
            return this;
        }

        /** Starts the elapsed timer now. */
        public Builder startNow() {
            this.startTimestampMillis = System.currentTimeMillis();
            return this;
        }

        /**
         * @param key  asset key uploaded in the Developer Portal's "Rich Presence →
         *             Art Assets" tab (e.g. {@code lucifer_logo}), or an image URL
         *             if the app allows external assets
         * @param text tooltip text shown under the image (ignored by current clients)
         */
        public Builder largeImage(String key, String text) {
            this.largeImageKey = key;
            this.largeImageText = truncate(text, 128);
            return this;
        }

        public Builder smallImage(String key, String text) {
            this.smallImageKey = key;
            this.smallImageText = truncate(text, 128);
            return this;
        }

        public Builder button(String label, String url) {
            if (buttons.size() < 2) {
                buttons.add(new String[]{truncate(label, 32), url});
            }
            return this;
        }

        public RichPresence build() {
            if ((details == null || details.isEmpty()) && (state == null || state.isEmpty())) {
                throw new IllegalStateException("a presence needs at least details or state");
            }
            return new RichPresence(this);
        }

        private static String truncate(String s, int max) {
            if (s == null) return null;
            return s.length() <= max ? s : s.substring(0, max);
        }
    }

    @Override
    public String toString() {
        return "RichPresence{" + details + " | " + state + '}';
    }
}

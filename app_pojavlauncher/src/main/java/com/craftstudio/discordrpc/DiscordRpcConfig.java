package com.craftstudio.discordrpc;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Connection settings for the Discord Rich Presence bridge.
 *
 * <pre>
 * DiscordRpcConfig config = DiscordRpcConfig.builder()
 *         .endpoint("http://192.168.1.42:6464")
 *         .token("shared-secret")           // optional, must match --token
 *         .build();                         // clientId defaults to LUCIFER
 * </pre>
 *
 * The endpoint points at {@code tools/discord-bridge/bridge.js}, running on a
 * desktop machine that has the Discord client logged in.
 */
public final class DiscordRpcConfig {

    /** Default port used by the bridge. */
    public static final int DEFAULT_PORT = 6464;

    /**
     * LUCIFER LAUNCHER's Discord application id (Developer Portal →
     * Application ID). The name shown on the card ("LUCIFER LAUNCHER") comes
     * from this application, not from the client.
     */
    public static final String LUCIFER_APPLICATION_ID = "1562498710527680572";

    private final URI endpoint;
    private final String clientId;
    private final String token;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final boolean enabled;

    private DiscordRpcConfig(Builder b) {
        this.endpoint = b.endpoint;
        this.clientId = b.clientId;
        this.token = b.token;
        this.connectTimeoutMs = b.connectTimeoutMs;
        this.readTimeoutMs = b.readTimeoutMs;
        this.enabled = b.enabled;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    /** Discord application id (Developer Portal → your app → Application ID). */
    public String getClientId() {
        return clientId;
    }

    public String getToken() {
        return token;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    /** Cheap user-facing kill switch ("Show my status on Discord"). */
    public boolean isEnabled() {
        return enabled;
    }

    public Builder toBuilder() {
        return new Builder()
                .endpoint(endpoint)
                .clientId(clientId)
                .token(token)
                .connectTimeoutMs(connectTimeoutMs)
                .readTimeoutMs(readTimeoutMs)
                .enabled(enabled);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private URI endpoint = URI.create("http://127.0.0.1:" + DEFAULT_PORT);
        private String clientId = LUCIFER_APPLICATION_ID;
        private String token = "";
        private int connectTimeoutMs = 4000;
        private int readTimeoutMs = 4000;
        private boolean enabled = true;

        public Builder endpoint(String url) {
            try {
                this.endpoint = new URI(url);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("bad endpoint: " + url, e);
            }
            return this;
        }

        public Builder endpoint(URI uri) {
            this.endpoint = uri;
            return this;
        }

        public Builder clientId(String id) {
            this.clientId = id == null ? "" : id.trim();
            return this;
        }

        public Builder token(String token) {
            this.token = token == null ? "" : token;
            return this;
        }

        public Builder connectTimeoutMs(int ms) {
            this.connectTimeoutMs = ms;
            return this;
        }

        public Builder readTimeoutMs(int ms) {
            this.readTimeoutMs = ms;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public DiscordRpcConfig build() {
            if (endpoint == null) throw new IllegalStateException("endpoint is required");
            if (clientId.isEmpty()) throw new IllegalStateException("clientId is required");
            if (!clientId.matches("\\d{5,25}")) {
                throw new IllegalStateException("clientId must be the numeric Discord application id");
            }
            return new DiscordRpcConfig(this);
        }
    }

    @Override
    public String toString() {
        return "DiscordRpcConfig{endpoint=" + endpoint
                + ", clientId=" + clientId
                + ", auth=" + (token.isEmpty() ? "off" : "on")
                + ", enabled=" + enabled + '}';
    }
}

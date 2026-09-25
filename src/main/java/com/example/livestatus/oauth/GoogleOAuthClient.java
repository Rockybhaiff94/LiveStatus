package com.example.livestatus.oauth;

import com.example.livestatus.config.PluginConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles communication with Google OAuth 2.0 endpoints directly from the Paper plugin.
 */
public class GoogleOAuthClient {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/youtube.readonly";

    private final PluginConfig config;
    private final Logger logger;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public record TokenResponse(String accessToken, String refreshToken, long expiresInMs) {}

    public GoogleOAuthClient(PluginConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getRequestTimeoutMs()))
                .build();
    }

    /**
     * Builds the Google OAuth consent URL with the bound CSRF state parameter.
     */
    public String buildAuthorizationUrl(String stateToken) {
        return AUTH_URL + "?" +
                "client_id=" + urlEncode(config.getClientId()) +
                "&redirect_uri=" + urlEncode(config.getRedirectUri()) +
                "&response_type=code" +
                "&scope=" + urlEncode(SCOPE) +
                "&access_type=offline" +
                "&prompt=consent" +
                "&state=" + urlEncode(stateToken);
    }

    /**
     * Exchanges an authorization code for an access token and refresh token.
     */
    public TokenResponse exchangeCode(String code) {
        String body = "code=" + urlEncode(code) +
                "&client_id=" + urlEncode(config.getClientId()) +
                "&client_secret=" + urlEncode(config.getClientSecret()) +
                "&redirect_uri=" + urlEncode(config.getRedirectUri()) +
                "&grant_type=authorization_code";

        return sendTokenRequest(body);
    }

    /**
     * Refreshes an expired access token using the stored refresh token.
     */
    public TokenResponse refreshToken(String refreshToken) {
        String body = "refresh_token=" + urlEncode(refreshToken) +
                "&client_id=" + urlEncode(config.getClientId()) +
                "&client_secret=" + urlEncode(config.getClientSecret()) +
                "&grant_type=refresh_token";

        return sendTokenRequest(body);
    }

    private TokenResponse sendTokenRequest(String formBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .timeout(Duration.ofMillis(config.getRequestTimeoutMs()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                if (config.isDebugEnabled()) {
                    logger.warning("Google token request failed with HTTP " + response.statusCode());
                }
                return null;
            }

            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            String accessToken = json.get("access_token").getAsString();
            String refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : null;
            long expiresInSeconds = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600;

            return new TokenResponse(accessToken, refreshToken, expiresInSeconds * 1000L);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Exception during Google OAuth token exchange: " + e.getMessage());
            return null;
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }
}


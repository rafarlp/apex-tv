package dev.apextv.firetv;

import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Parses only token responses delivered over F1's authenticated HTTPS endpoint. */
final class OAuthTokens {
    static JSONObject session(JSONObject tokens, JSONObject previous, long now) throws Exception {
        long ttl = tokens.optLong("expires_in");
        if (!"bearer".equalsIgnoreCase(tokens.optString("token_type")) || ttl <= 0 || ttl > 86_400)
            throw new IOException("Invalid F1 TV token response");
        JSONObject access = claims(tokens.getString("access_token"));
        String playback = access.optString("subscriptionToken", previous == null ? "" : previous.optString("playback"));
        String refresh = tokens.optString("refresh_token", previous == null ? "" : previous.optString("refresh"));
        JSONObject profile = claims(playback);
        if (refresh.isEmpty() || playback.isEmpty() || profile.optLong("exp") <= now || profile.optString("SessionId").isEmpty())
            throw new IOException("Account session expired. Sign in again.");
        return new JSONObject().put("refresh", refresh).put("playback", playback)
            .put("expires", Math.min(now + ttl, profile.getLong("exp")))
            .put("playbackExpires", profile.getLong("exp")).put("sessionId", profile.getString("SessionId"));
    }
    private static JSONObject claims(String token) throws Exception {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) throw new IOException("Invalid account token");
        try { return new JSONObject(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid account token"); }
    }
    private OAuthTokens() {}
}

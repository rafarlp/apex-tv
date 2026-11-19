package dev.apextv.firetv;

import org.json.JSONObject;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.Assert.*;

public class OAuthTokensTest {
    private static String jwt(JSONObject claims) {
        return "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toString().getBytes(StandardCharsets.UTF_8)) + ".signature";
    }
    private static JSONObject response(String playback, long ttl) throws Exception {
        return new JSONObject().put("token_type", "Bearer").put("expires_in", ttl).put("refresh_token", "refresh1")
            .put("access_token", jwt(new JSONObject().put("subscriptionToken", playback)));
    }
    @Test public void neverExtendsPlaybackCredentialPastItsActualExpiry() throws Exception {
        String playback = jwt(new JSONObject().put("SessionId", "session").put("exp", 2000));
        JSONObject next = OAuthTokens.session(response(playback, 3600), null, 1000);
        assertEquals(2000, next.getLong("expires")); assertEquals(2000, next.getLong("playbackExpires"));
    }
    @Test public void refreshCanOmitPlaybackTokenAndRotatesRefreshToken() throws Exception {
        String playback = jwt(new JSONObject().put("SessionId", "session").put("exp", 10000));
        JSONObject first = OAuthTokens.session(response(playback, 3600), null, 1000);
        JSONObject refreshed = new JSONObject().put("token_type", "Bearer").put("expires_in", 3600)
            .put("access_token", jwt(new JSONObject().put("sub", "account"))).put("refresh_token", "refresh2");
        JSONObject next = OAuthTokens.session(refreshed, first, 4000);
        assertEquals(playback, next.getString("playback")); assertEquals("refresh2", next.getString("refresh"));
        assertEquals(10000, next.getLong("playbackExpires"));
    }
    @Test public void rejectsExpiredPlaybackAndMissingRefreshCredentials() throws Exception {
        String expired = jwt(new JSONObject().put("SessionId", "session").put("exp", 999));
        assertThrows(Exception.class, () -> OAuthTokens.session(response(expired, 3600), null, 1000));
        String valid = jwt(new JSONObject().put("SessionId", "session").put("exp", 10000));
        JSONObject missing = response(valid, 3600); missing.remove("refresh_token");
        assertThrows(Exception.class, () -> OAuthTokens.session(missing, null, 1000));
    }
    @Test public void rejectsMalformedTokenAndInvalidLifetime() throws Exception {
        assertThrows(Exception.class, () -> OAuthTokens.session(response("not-a-jwt", 3600), null, 1000));
        String valid = jwt(new JSONObject().put("SessionId", "session").put("exp", 10000));
        assertThrows(Exception.class, () -> OAuthTokens.session(response(valid, -1), null, 1000));
        assertThrows(Exception.class, () -> OAuthTokens.session(response(valid, Long.MAX_VALUE), null, 1000));
    }
}

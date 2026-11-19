package dev.apextv.firetv;

import android.content.Context;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Map;

final class Auth {
    private static Auth instance;
    static synchronized Auth get(Context context) {
        if (instance == null) instance = new Auth(context);
        return instance;
    }
    final SessionStore store;
    private JSONObject session;
    private boolean loaded;
    private Auth(Context context) { store = new SessionStore(context.getApplicationContext()); }
    synchronized boolean signedIn() throws Exception {
        load(); return session != null && session.optLong("playbackExpires") > System.currentTimeMillis() / 1000;
    }
    private void load() throws Exception {
        if (!loaded) {
            try { session = store.load(); }
            finally { loaded = true; }
        }
    }
    synchronized void clear() { store.clear(); session = null; loaded = true; }
    JSONObject begin() throws Exception {
        Network.Response r = Network.form("device_authorization", Map.of("client_id", Protocol.TV_CLIENT, "scope", "openid f1tv_groups"));
        if (r.status != 200) throw new IOException("F1 TV could not start sign-in (HTTP " + r.status + ")");
        String url = r.json.optString("verification_uri_complete", r.json.optString("verification_uri"));
        if (!Protocol.verificationUrl(url) || !Protocol.verificationUrl(r.json.optString("verification_uri")) || r.json.optString("device_code").isEmpty() ||
                r.json.optString("user_code").isEmpty() || r.json.optLong("expires_in") <= 0 || r.json.optLong("expires_in") > 86_400)
            throw new IOException("F1 TV returned an invalid sign-in response");
        return r.json;
    }
    Network.Response poll(String deviceCode) throws Exception {
        return Network.form("token", Map.of("client_id", Protocol.TV_CLIENT,
            "grant_type", "urn:ietf:params:oauth:grant-type:device_code", "device_code", deviceCode));
    }
    synchronized void accept(JSONObject tokens) throws Exception { load(); saveTokens(tokens); }
    private void saveTokens(JSONObject tokens) throws Exception {
        JSONObject next = OAuthTokens.session(tokens, session, System.currentTimeMillis() / 1000);
        store.save(next); session = next;
    }
    synchronized String token() throws Exception {
        load();
        long now = System.currentTimeMillis() / 1000;
        if (session == null || session.optLong("playbackExpires") <= now) throw new IOException("Sign in to F1 TV again");
        if (session.optLong("expires") <= now + 60) {
            Network.Response r = Network.form("token", Map.of("client_id", Protocol.TV_CLIENT, "grant_type", "refresh_token", "refresh_token", session.getString("refresh")));
            if (r.status != 200) {
                if ("invalid_grant".equals(r.json.optString("error"))) clear();
                throw new IOException("F1 TV could not renew the account session. Sign in again.");
            }
            saveTokens(r.json);
        }
        return session.getString("playback");
    }
    synchronized String sessionId() throws Exception { load(); return session == null ? "" : session.getString("sessionId"); }
}

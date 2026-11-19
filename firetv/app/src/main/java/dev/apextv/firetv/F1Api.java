package dev.apextv.firetv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class F1Api {
    private final Auth auth;
    private final String playbackSession = UUID.randomUUID().toString();
    F1Api(Auth auth) { this.auth = auth; }
    List<Protocol.Entry> page(String uri) throws Exception {
        return Protocol.catalog(Network.get(Protocol.catalogUrl(uri), Map.of("User-Agent", Protocol.USER_AGENT)));
    }
    static final class Feed {
        final String title, channel;
        Feed(String title, String channel) { this.title = title; this.channel = channel; }
    }
    List<Feed> feeds(String id) throws Exception {
        Protocol.contentId(id);
        JSONObject payload = Protocol.payload(Network.get(Protocol.BASE + "/3.0/R/ENG/BIG_SCREEN_HLS/ALL/CONTENT/VIDEO/" + id + "/F1_TV_Pro_Annual/14", headers(true)));
        JSONArray nodes = payload.optJSONArray("containers");
        List<Feed> feeds = new ArrayList<>();
        if (nodes != null && nodes.length() > 0) {
            JSONObject node = nodes.optJSONObject(0);
            JSONObject metadata = node == null ? null : node.optJSONObject("metadata");
            JSONArray streams = metadata == null ? null : metadata.optJSONArray("additionalStreams");
            if (streams != null) for (int i = 0; i < streams.length(); i++) {
                JSONObject s = streams.optJSONObject(i);
                if (s == null) continue;
                String channel = s.optString("channelId", "");
                if (!channel.matches("\\d+")) continue;
                String name = s.optString("title", "Feed");
                if ("obc".equals(s.optString("type")))
                    name = (s.optString("driverFirstName") + " " + s.optString("driverLastName")).trim() + " · " + name;
                feeds.add(new Feed(name, "INTERNATIONAL".equals(s.optString("title")) ? "" : channel));
            }
        }
        if (feeds.isEmpty()) feeds.add(new Feed("Main broadcast", ""));
        return feeds;
    }
    Map<String, String> headers(boolean video) throws Exception {
        String token = auth.token();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", Protocol.USER_AGENT);
        headers.put("Origin", Protocol.BASE); headers.put("Referer", Protocol.BASE + "/");
        headers.put("x-f1-device-info", Protocol.TV_PROFILE);
        headers.put("ascendontoken", token);
        headers.put("entitlementtoken", video ? token : auth.sessionId());
        headers.put("sessionid", playbackSession);
        headers.put("CD-DeviceType", "49");
        headers.put("CD-DistributionChannel", "e827264e-a81b-4f65-a567-2070850c128e");
        headers.put("Cache-Control", "no-cache");
        return headers;
    }
    static final class Playback {
        final String manifest, license, streamType, token, entitlement;
        Playback(String manifest, String license, String streamType, String token, String entitlement) {
            this.manifest = manifest; this.license = license; this.streamType = streamType;
            this.token = token; this.entitlement = entitlement;
        }
    }
    Playback playback(String id, String channel, Protocol.Route route) throws Exception {
        Map<String, String> headers = headers(false);
        // Fetch account entitlements first; the PLAY result supplies the stream-specific token.
        JSONObject entitlement = Protocol.payload(Network.get(Protocol.BASE + "/2.0/R/ENG/BIG_SCREEN_HLS/ALL/USER/ENTITLEMENT", headers));
        String accountEntitlement = entitlement.optString("entitlementToken");
        if (!accountEntitlement.isEmpty()) headers.put("entitlementtoken", accountEntitlement);
        if (!route.override.isEmpty()) headers.put("x-f1-override-video-stream", route.override);
        JSONObject response = Protocol.payload(Network.get(Protocol.playUrl(id, channel, route), headers));
        String drm = response.optString("drmType").toLowerCase(java.util.Locale.ROOT);
        if (!drm.isEmpty() && !drm.contains("widevine")) throw new IOException("This route returned " + Protocol.safeCode(drm) + " instead of Android Widevine. Try another playback route.");
        String manifest = response.optString("url");
        if (manifest.isEmpty()) {
            JSONObject tme = response.optJSONObject("tme");
            JSONArray feeds = tme == null ? null : tme.optJSONArray("feeds");
            if (feeds != null) for (int i = 0; i < feeds.length(); i++) {
                JSONObject feed = feeds.optJSONObject(i);
                if (feed == null) continue;
                JSONObject meta = feed.optJSONObject("metadata");
                if (channel.isEmpty() || meta != null && channel.equals(meta.optString("channel_id"))) {
                    manifest = feed.optString("url"); break;
                }
            }
        }
        try {
            URI media = URI.create(manifest);
            if (!"https".equals(media.getScheme()) || media.getHost() == null || media.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw new IOException("F1 TV did not return a playable stream. Try another route."); }
        String license = response.optString("laURL", response.optString("laUrl", ""));
        if (!license.isEmpty() && !Protocol.firstParty(license, "f1tv.formula1.com"))
            throw new IOException("Unexpected DRM license service address");
        if (!drm.isEmpty() && license.isEmpty()) throw new IOException("F1 TV did not return a Widevine license address");
        return new Playback(manifest, license, response.optString("streamType", "unknown"),
            headers.get("ascendontoken"), response.optString("entitlementToken", headers.get("entitlementtoken")));
    }
}

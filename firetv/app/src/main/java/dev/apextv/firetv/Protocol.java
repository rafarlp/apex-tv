package dev.apextv.firetv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Wire handling shared by the app and JVM tests. No accounts or secrets in source. */
final class Protocol {
    static final String BASE = "https://f1tv.formula1.com";
    static final String TV_CLIENT = "13c21153-f2f3-484b-a642-f2a2927e55de";
    static final String TV_PROFILE = "device=android_tv;screen=bigscreen;os=android;model=kirkwood;osVersion=34;appVersion=30482001;playerVersion=3.112.0;tms=1;";
    static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Google TV Streamer; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.114 Safari/537.36";
    enum Route {
        UHD_DASH("UHD / HDR · DASH", "BIG_SCREEN_HLS", "HDR_UHD_DASHWV"),
        UHD_HLS("UHD / HDR · HLS", "BIG_SCREEN_HLS", "HDR_UHD_CMAFWV"),
        TV_AUTO("TV automatic", "BIG_SCREEN_HLS", ""),
        HD("HD compatibility", "WEB_DASH", "");
        final String label, platform, override;
        Route(String label, String platform, String override) { this.label = label; this.platform = platform; this.override = override; }
    }
    static final class Entry {
        final String title, uri, contentId, subtitle;
        Entry(String title, String uri, String contentId, String subtitle) {
            this.title = title; this.uri = uri; this.contentId = contentId; this.subtitle = subtitle;
        }
        boolean video() { return !contentId.isEmpty(); }
        @Override public String toString() { return title + (subtitle.isEmpty() ? "" : "\n" + subtitle); }
    }
    static boolean firstParty(String value, String host) {
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && host.equalsIgnoreCase(uri.getHost()) &&
                uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (RuntimeException e) { return false; }
    }
    static String catalogUrl(String uri) {
        String value = uri.startsWith("/") && !uri.startsWith("//") ? BASE + uri : uri;
        if (!firstParty(value, "f1tv.formula1.com")) throw new IllegalArgumentException("Unsupported catalog address");
        return value;
    }
    static boolean verificationUrl(String value) {
        if (firstParty(value, "account.formula1.com")) return true;
        // F1's device-authorization response uses its official /pair short link.
        return firstParty(value, "f1.com") && "/pair".equals(URI.create(value).getPath());
    }
    static String contentId(String input) {
        String trimmed = input.trim();
        if (trimmed.matches("[0-9]{5,15}")) return trimmed;
        if (!firstParty(trimmed, "f1tv.formula1.com")) throw new IllegalArgumentException("Enter an F1 TV session link or content ID");
        Matcher match = Pattern.compile("/(?:detail|CONTENT/VIDEO)/(\\d{5,15})(?:/|$|\\?)").matcher(URI.create(trimmed).getPath());
        if (!match.find()) throw new IllegalArgumentException("This link does not identify a session");
        return match.group(1);
    }
    static JSONObject payload(JSONObject envelope) {
        if (!"OK".equalsIgnoreCase(envelope.optString("resultCode")))
            throw new IllegalArgumentException("F1 TV rejected this request (" + safeCode(envelope.optString("errorDescription")) + ")");
        JSONObject result = envelope.optJSONObject("resultObj");
        if (result == null) throw new IllegalArgumentException("F1 TV returned an incomplete response");
        return result;
    }
    static String safeCode(String value) { return value.matches("[A-Za-z0-9_-]{1,50}") ? value : "unavailable"; }
    static List<Entry> catalog(JSONObject envelope) {
        LinkedHashMap<String, Entry> found = new LinkedHashMap<>();
        walk(payload(envelope), found, 0);
        return new ArrayList<>(found.values());
    }
    private static void walk(Object value, Map<String, Entry> found, int depth) {
        if (depth > 24 || found.size() >= 300) return;
        if (value instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) walk(array.opt(i), found, depth + 1);
        } else if (value instanceof JSONObject node) {
            JSONObject m = node.optJSONObject("metadata");
            JSONArray actions = node.optJSONArray("actions");
            if (m != null && actions != null) {
                String title = m.optString("title", m.optString("label", ""));
                for (int i = 0; i < actions.length(); i++) {
                    JSONObject action = actions.optJSONObject(i);
                    if (action == null) continue;
                    String uri = action.optString("uri");
                    if (uri.isEmpty() || title.isEmpty()) continue;
                    try { catalogUrl(uri); } catch (IllegalArgumentException e) { continue; }
                    boolean video = "VIDEO".equals(m.optString("contentType")) && uri.contains("/CONTENT/VIDEO/");
                    String id = video ? m.optString("contentId", "") : "";
                    if (video && !id.matches("\\d+")) continue;
                    if (!video && !uri.contains("/PAGE/")) continue;
                    String key = video ? "video:" + id : "page:" + uri;
                    String subtitle = video ? m.optString("contentSubtype", "") : "Browse";
                    found.putIfAbsent(key, new Entry(title, uri, id, subtitle));
                }
            }
            for (var keys = node.keys(); keys.hasNext();) {
                String key = keys.next();
                if (!key.equals("metadata") && !key.equals("actions") && !key.equals("translations")) walk(node.opt(key), found, depth + 1);
            }
        }
    }
    static String playUrl(String id, String channel, Route route) {
        if (!id.matches("\\d{5,15}") || (!channel.isEmpty() && !channel.matches("\\d+")))
            throw new IllegalArgumentException("Invalid content or channel ID");
        return BASE + "/3.0/R/ENG/" + route.platform + "/ALL/CONTENT/PLAY?contentId=" + id + "&player=player_bm" +
            (channel.isEmpty() ? "" : "&channelId=" + channel);
    }
    static boolean renderedUhd(int width, int height, int renderedFrames) {
        return width >= 3840 && height >= 2160 && renderedFrames > 0;
    }
    private Protocol() {}
}

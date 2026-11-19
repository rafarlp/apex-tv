package dev.apextv.firetv;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;

final class Network {
    static final class Response {
        final int status; final JSONObject json;
        Response(int status, JSONObject json) { this.status = status; this.json = json; }
    }
    static JSONObject get(String url, Map<String, String> headers) throws Exception {
        Response response = request(url, null, headers);
        if (response.status < 200 || response.status >= 300) throw new IOException("F1 TV HTTP " + response.status);
        return response.json;
    }
    static Response form(String path, Map<String, String> params) throws Exception {
        StringBuilder body = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return request("https://id.formula1.com/as/" + path, body.toString().getBytes(StandardCharsets.UTF_8), Map.of());
    }
    private static Response request(String url, byte[] body, Map<String, String> headers) throws Exception {
        if (!Protocol.firstParty(url, "f1tv.formula1.com") && !Protocol.firstParty(url, "id.formula1.com"))
            throw new IOException("Unsupported account service address");
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15_000); connection.setReadTimeout(25_000);
        connection.setRequestProperty("Accept", "application/json");
        headers.forEach(connection::setRequestProperty);
        try {
            if (body != null) {
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (var stream = connection.getOutputStream()) { stream.write(body); }
            }
            int code = connection.getResponseCode();
            try (InputStream in = code >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                if (in == null) return new Response(code, new JSONObject());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > 4 * 1024 * 1024) throw new IOException("Service response is too large");
                    out.write(buffer, 0, n);
                }
                try { return new Response(code, new JSONObject(out.toString("UTF-8"))); }
                catch (org.json.JSONException e) { throw new IOException("F1 TV returned a non-JSON response (HTTP " + code + ")"); }
            }
        } finally { connection.disconnect(); }
    }
    private Network() {}
}

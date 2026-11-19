package dev.apextv.firetv;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MainActivity extends ComponentActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService loginWorker = Executors.newSingleThreadExecutor();
    private Future<?> loginTask;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String[]> history = new ArrayDeque<>();
    private Auth auth;
    private F1Api api;
    private TextView status;
    private ListView list;
    private String currentUri = "", currentTitle = "";
    private int screen;
    private volatile int loginGeneration;
    private boolean pairing;
    private int pageGeneration;
    private Protocol.Route route = Protocol.Route.UHD_DASH;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); auth = Auth.get(this); api = new F1Api(auth);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        int savedRoute = getPreferences(0).getInt("route", 0);
        if (savedRoute >= 0 && savedRoute < Protocol.Route.values().length) route = Protocol.Route.values()[savedRoute];
        showHome();
    }
    private synchronized void showHome() {
        if (loginTask != null) loginTask.cancel(true);
        pairing = false; loginGeneration++; screen++;
        LinearLayout root = TvUi.column(this); TvUi.heading(this, root, "Your race weekend.");
        LinearLayout controls = TvUi.row(this);
        controls.addView(TvUi.button(this, "Browse", () -> loadMenu(true)));
        controls.addView(TvUi.button(this, "Open session", this::openSession));
        controls.addView(TvUi.button(this, "Playback route", this::pickRoute));
        controls.addView(TvUi.button(this, "Account", this::account));
        controls.addView(TvUi.button(this, "Device", this::deviceInfo));
        root.addView(controls);
        status = TvUi.text(this, "Native F1 TV client · " + route.label, 14); status.setTextColor(0xFFB4B8C4); root.addView(status);
        list = new ListView(this); list.setDividerHeight(TvUi.dp(this, 4)); list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setSelector(new ColorDrawable(0xFF913129)); root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView footer = TvUi.text(this, "D-pad to browse · Select to open · Back to return · F1 TV Premium required for 4K", 12);
        footer.setTextColor(0xFF8D929E); root.addView(footer); setContentView(root);
        loadMenu(false);
    }
    private void pickRoute() {
        String[] names = new String[Protocol.Route.values().length];
        for (int i = 0; i < names.length; i++) names[i] = Protocol.Route.values()[i].label;
        new AlertDialog.Builder(this).setTitle("Playback route").setSingleChoiceItems(names, route.ordinal(), (dialog, which) -> {
            route = Protocol.Route.values()[which]; getPreferences(0).edit().putInt("route", which).apply();
            status.setText("Native F1 TV client · " + route.label); dialog.dismiss();
        }).setNegativeButton("Close", null).show();
    }
    private void openSession() {
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("F1 TV link or content ID");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Open session")
            .setView(input).setPositiveButton("Open", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            try { String id = Protocol.contentId(input.getText().toString()); dialog.dismiss(); chooseFeed(id, "F1 TV session"); }
            catch (IllegalArgumentException e) { input.setError(e.getMessage()); }
        })); dialog.show();
    }
    private void loadMenu(boolean reset) {
        if (reset) history.clear();
        loadPage("/2.0/R/ENG/WEB_HLS/ALL/MENU/PREMIUM/14", "Browse", false);
    }
    private void loadPage(String uri, String title, boolean push) {
        if (push && !currentUri.isEmpty()) history.push(new String[]{currentUri, currentTitle});
        currentUri = uri; currentTitle = title; int generation = ++pageGeneration; int view = screen;
        status.setText("Loading " + title + "…"); list.setAdapter(null);
        worker.execute(() -> {
            try {
                List<Protocol.Entry> entries = api.page(uri);
                main.post(() -> {
                    if (generation != pageGeneration || view != screen || isFinishing()) return;
                    status.setText(title + " · " + route.label + (entries.isEmpty() ? " · No sessions available" : ""));
                    ArrayAdapter<Protocol.Entry> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, entries) {
                        @Override public View getView(int position, View convertView, ViewGroup parent) {
                            TextView row = (TextView) super.getView(position, convertView, parent);
                            row.setTextSize(17); row.setTextColor(Color.WHITE); row.setMinHeight(TvUi.dp(MainActivity.this, 62));
                            row.setPadding(TvUi.dp(MainActivity.this, 16), TvUi.dp(MainActivity.this, 10), TvUi.dp(MainActivity.this, 16), TvUi.dp(MainActivity.this, 10));
                            return row;
                        }
                    };
                    list.setAdapter(adapter); list.setOnItemClickListener((p, v, pos, id) -> {
                        Protocol.Entry entry = entries.get(pos);
                        if (entry.video()) chooseFeed(entry.contentId, entry.title); else loadPage(entry.uri, entry.title, true);
                    });
                    list.requestFocus();
                });
            } catch (Exception e) { main.post(() -> { if (generation == pageGeneration && view == screen) status.setText(TvUi.error(e)); }); }
        });
    }
    private void chooseFeed(String id, String title) {
        int view = screen; status.setText("Loading feeds…");
        worker.execute(() -> {
            try {
                if (!auth.signedIn()) { main.post(this::showPairing); return; }
                List<F1Api.Feed> feeds = api.feeds(id);
                main.post(() -> {
                    if (view != screen || isFinishing()) return;
                    status.setText(title);
                    String[] names = feeds.stream().map(f -> f.title).toArray(String[]::new);
                    new AlertDialog.Builder(this).setTitle("Choose feed").setItems(names, (d, which) -> {
                        F1Api.Feed feed = feeds.get(which);
                        Intent intent = new Intent(this, PlayerActivity.class);
                        intent.putExtra("contentId", id); intent.putExtra("channel", feed.channel);
                        intent.putExtra("title", title + " · " + feed.title); intent.putExtra("route", route.ordinal());
                        startActivity(intent);
                    }).setNegativeButton("Cancel", null).show();
                });
            } catch (Exception e) { main.post(() -> { if (view == screen) status.setText(TvUi.error(e)); }); }
        });
    }
    private void account() {
        worker.execute(() -> {
            try {
                boolean signedIn = auth.signedIn();
                main.post(() -> {
                    if (isFinishing()) return;
                    if (!signedIn) { showPairing(); return; }
                    new AlertDialog.Builder(this).setTitle("F1 TV account").setMessage("Your F1 TV account is signed in on this device.")
                        .setPositiveButton("Done", null).setNeutralButton("Sign in again", (d, w) -> showPairing())
                        .setNegativeButton("Sign out", (d, w) -> worker.execute(() -> {
                            auth.clear(); main.post(() -> status.setText("Signed out"));
                        })).show();
                });
            } catch (Exception e) { main.post(() -> status.setText("Account storage could not be read. Open Account and sign in again.")); }
        });
    }
    private synchronized void showPairing() {
        if (isFinishing()) return;
        if (loginTask != null) loginTask.cancel(true);
        pairing = true; int generation = ++loginGeneration; screen++; pageGeneration++;
        LinearLayout root = TvUi.column(this); TvUi.heading(this, root, "Sign in with F1 TV.");
        TextView instructions = TvUi.text(this, "Getting a sign-in code…", 18); root.addView(instructions);
        LinearLayout pair = TvUi.row(this); pair.setGravity(Gravity.CENTER_VERTICAL);
        ImageView qr = new ImageView(this); qr.setContentDescription("F1 TV sign-in QR code");
        pair.addView(qr, new LinearLayout.LayoutParams(TvUi.dp(this, 190), TvUi.dp(this, 190)));
        TextView code = TvUi.text(this, "", 26); code.setPadding(TvUi.dp(this, 28), 0, 0, 0); pair.addView(code); root.addView(pair);
        TextView progress = TvUi.text(this, "Use your phone or computer to sign in directly with Formula 1.", 15); root.addView(progress);
        LinearLayout controls = TvUi.row(this);
        controls.addView(TvUi.button(this, "New code", this::showPairing)); controls.addView(TvUi.button(this, "Back", this::showHome)); root.addView(controls);
        setContentView(root); controls.getChildAt(1).requestFocus();
        loginTask = loginWorker.submit(() -> {
            try {
                JSONObject authorization = auth.begin();
                String uri = authorization.optString("verification_uri_complete", authorization.getString("verification_uri"));
                Bitmap image = qr(uri);
                main.post(() -> {
                    if (generation != loginGeneration) return;
                    instructions.setText("Scan the QR code, or open this address and enter the code:");
                    qr.setImageBitmap(image); code.setText(authorization.optString("verification_uri") + "\n\n" + authorization.optString("user_code"));
                });
                long expiry = SystemClock.elapsedRealtime() + authorization.getLong("expires_in") * 1000;
                long interval = Math.max(1, authorization.optLong("interval", 5)) * 1000;
                while (generation == loginGeneration && !Thread.currentThread().isInterrupted()) {
                    Thread.sleep(Math.min(interval, Math.max(1, expiry - SystemClock.elapsedRealtime())));
                    if (generation != loginGeneration) return;
                    if (SystemClock.elapsedRealtime() >= expiry) throw new java.io.IOException("This code expired. Choose New code.");
                    Network.Response result;
                    try { result = auth.poll(authorization.getString("device_code")); }
                    catch (java.net.SocketTimeoutException e) { interval = Math.min(60_000, interval * 2); continue; }
                    if (generation != loginGeneration) return;
                    if (result.status == 200) {
                        // Accept under the same generation lock used by Back, New code, and destruction.
                        main.post(() -> {
                            if (generation != loginGeneration) return;
                            worker.execute(() -> {
                                synchronized (MainActivity.this) {
                                    if (generation != loginGeneration) return;
                                    try { auth.accept(result.json); main.post(() -> { if (generation == loginGeneration) showHome(); }); }
                                    catch (Exception e) { main.post(() -> { if (generation == loginGeneration) progress.setText(TvUi.error(e)); }); }
                                }
                            });
                        });
                        return;
                    }
                    if (result.status >= 500) { interval = Math.min(60_000, interval * 2); continue; }
                    String error = result.json.optString("error");
                    if ("authorization_pending".equals(error)) continue;
                    if ("slow_down".equals(error)) { interval += 5000; continue; }
                    throw new java.io.IOException("expired_token".equals(error) ? "This code expired. Choose New code." : "F1 TV did not approve sign-in. Choose New code to retry.");
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (Exception e) { main.post(() -> { if (generation == loginGeneration) progress.setText(TvUi.error(e)); }); }
        });
    }
    private static Bitmap qr(String value) throws Exception {
        var matrix = new MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 380, 380);
        Bitmap image = Bitmap.createBitmap(380, 380, Bitmap.Config.RGB_565);
        for (int y = 0; y < 380; y++) for (int x = 0; x < 380; x++) image.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
        return image;
    }
    private void deviceInfo() {
        new AlertDialog.Builder(this).setTitle("Device playback capabilities")
            .setMessage(DeviceCapabilities.describe(this)).setPositiveButton("Close", null).show();
    }
    private void back() {
        if (pairing) { showHome(); return; }
        if (!history.isEmpty()) { String[] previous = history.pop(); loadPage(previous[0], previous[1], false); }
        else finish();
    }
    @Override protected synchronized void onDestroy() {
        loginGeneration++; pageGeneration++; main.removeCallbacksAndMessages(null); worker.shutdownNow(); loginWorker.shutdownNow(); super.onDestroy();
    }
}

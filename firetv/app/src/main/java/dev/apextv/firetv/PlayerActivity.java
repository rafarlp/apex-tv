package dev.apextv.firetv;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@OptIn(markerClass = UnstableApi.class)
public final class PlayerActivity extends ComponentActivity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ExoPlayer player;
    private DefaultTrackSelector selector;
    private PlayerView surface;
    private TextView stats;
    private LinearLayout actions;
    private String contentId, channel, title, sourceType = "Waiting for stream", errorText = "", decoder = "pending";
    private Protocol.Route route;
    private int generation, width, height, maxHeight;
    private int framesAtSizeChange;
    private boolean frameRendered, stopped;
    private boolean controlsVisible = true, statsPinned, tunneling, frameRateMatching;
    private final PlaybackWindow recentFrames = new PlaybackWindow();
    private int bufferingEvents;
    private boolean hasPlayed;
    private float matchedFps = -1;
    private int matchedWidth, matchedHeight;
    private long resumePosition = C.TIME_UNSET;
    private boolean resumePlaying = true;
    private String drmLevel = "checking";
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (player != null) {
                var counters = player.getVideoDecoderCounters();
                if (counters != null) {
                    counters.ensureUpdated();
                    recentFrames.record(SystemClock.elapsedRealtime(), counters.renderedOutputBufferCount, counters.droppedBufferCount, player.isPlaying());
                }
            }
            updateStats(); if (!stopped) main.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        tunneling = getPreferences(0).getBoolean("tunneling", false);
        frameRateMatching = getPreferences(0).getBoolean("frameRateMatching", true);
        contentId = getIntent().getStringExtra("contentId"); channel = getIntent().getStringExtra("channel"); title = getIntent().getStringExtra("title");
        int index = getIntent().getIntExtra("route", 0);
        route = Protocol.Route.values()[Math.max(0, Math.min(Protocol.Route.values().length - 1, index))];
        if (channel == null) channel = "";
        if (title == null) title = "F1 TV";
        if (contentId == null || !contentId.matches("\\d{5,15}")) { finish(); return; }
        setContentView(R.layout.player); surface = findViewById(R.id.player); stats = findViewById(R.id.stats); actions = findViewById(R.id.actions);
        actions.addView(TvUi.button(this, "Tracks", this::tracks));
        actions.addView(TvUi.button(this, "Route", this::routeMenu));
        actions.addView(TvUi.button(this, "Playback", this::playbackOptions));
        actions.addView(TvUi.button(this, "Live", () -> { if (player != null) { player.seekToDefaultPosition(); player.play(); } }));
        actions.addView(TvUi.button(this, "Pin stats", () -> { statsPinned = !statsPinned; overlayVisibility(); }));
        surface.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            controlsVisible = visibility == View.VISIBLE;
            actions.setVisibility(visibility); overlayVisibility();
        });
        surface.setControllerAutoShow(true); surface.requestFocus();
        if (frameRateMatching) DeviceCapabilities.preferUhdOutput(this);
    }
    @Override protected void onStart() {
        super.onStart(); stopped = false;
        if (surface != null) { load(); main.post(tick); }
    }
    private void load() {
        releasePlayer(); int attempt = ++generation;
        errorText = ""; sourceType = "Requesting " + route.label; frameRendered = false; decoder = "pending";
        width = height = maxHeight = 0; updateStats();
        framesAtSizeChange = 0; recentFrames.clear(); bufferingEvents = 0; hasPlayed = false;
        matchedFps = -1; matchedWidth = matchedHeight = 0;
        worker.execute(() -> {
            try {
                F1Api.Playback playback = new F1Api(Auth.get(this)).playback(contentId, channel, route);
                String level = DeviceCapabilities.drmLevel();
                main.post(() -> { if (attempt == generation && !stopped && !isFinishing()) { drmLevel = level; prepare(playback); } });
            } catch (Exception e) { main.post(() -> { if (attempt == generation && !stopped) failed(TvUi.error(e)); }); }
        });
    }
    private void prepare(F1Api.Playback playback) {
        sourceType = playback.streamType;
        DefaultHttpDataSource.Factory mediaHttp = new DefaultHttpDataSource.Factory()
            .setUserAgent(Protocol.USER_AGENT).setConnectTimeoutMs(15_000).setReadTimeoutMs(25_000)
            .setDefaultRequestProperties(Map.of("Origin", Protocol.BASE, "Referer", Protocol.BASE + "/"));
        // Only the stream entitlement reaches media delivery; account tokens are scoped to the license POST.
        mediaHttp.setDefaultRequestProperties(playback.entitlement.isEmpty()
            ? Map.of("Origin", Protocol.BASE, "Referer", Protocol.BASE + "/")
            : Map.of("Origin", Protocol.BASE, "Referer", Protocol.BASE + "/", "Cookie", "playToken=" + playback.entitlement));
        selector = new DefaultTrackSelector(this);
        selector.setParameters(selector.buildUponParameters()
            .setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE, false)
            .setExceedVideoConstraintsIfNecessary(false).setExceedRendererCapabilitiesIfNecessary(false)
            // "Automatic" must be adaptive rather than pinning the largest stream.
            .setForceHighestSupportedBitrate(false).setTunnelingEnabled(tunneling));
        // A bounded buffer matters on the first-generation Max's 2 GB/32-bit environment.
        DefaultLoadControl buffer = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 30_000, 1500, 3000).setTargetBufferBytes(48 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false).build();
        player = new ExoPlayer.Builder(this).setTrackSelector(selector).setLoadControl(buffer)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(mediaHttp)).build();
        surface.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onVideoSizeChanged(VideoSize size) {
                width = size.width; height = size.height;
                var counters = player == null ? null : player.getVideoDecoderCounters();
                if (counters != null) counters.ensureUpdated();
                framesAtSizeChange = counters == null ? 0 : counters.renderedOutputBufferCount;
                updateStats();
            }
            @Override public void onRenderedFirstFrame() { frameRendered = true; updateStats(); }
            @Override public void onTracksChanged(Tracks tracks) {
                maxHeight = 0;
                for (Tracks.Group group : tracks.getGroups()) if (group.getType() == C.TRACK_TYPE_VIDEO)
                    for (int i = 0; i < group.length; i++) maxHeight = Math.max(maxHeight, group.getTrackFormat(i).height);
                updateStats();
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING && hasPlayed) bufferingEvents++;
            }
            @Override public void onIsPlayingChanged(boolean playing) { if (playing) hasPlayed = true; }
            @Override public void onPlayerError(PlaybackException error) { failed("Playback failed: " + error.getErrorCodeName() + ". Try TV automatic or HD compatibility to compare."); }
        });
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override public void onVideoDecoderInitialized(EventTime time, String name, long initializedTimestampMs, long durationMs) { decoder = name; updateStats(); }
            @Override public void onVideoInputFormatChanged(EventTime time, Format format, DecoderReuseEvaluation reuse) {
                if (frameRateMatching && (format.frameRate != matchedFps || format.width != matchedWidth || format.height != matchedHeight)) {
                    matchedFps = format.frameRate; matchedWidth = format.width; matchedHeight = format.height;
                    DeviceCapabilities.matchFrameRate(PlayerActivity.this, format.width, format.height, format.frameRate);
                }
            }
        });
        MediaItem.Builder item = new MediaItem.Builder().setUri(playback.manifest).setMediaId(contentId);
        if (playback.streamType.toUpperCase(Locale.ROOT).contains("DASH") || Uri.parse(playback.manifest).getPath().endsWith(".mpd")) item.setMimeType(MimeTypes.APPLICATION_MPD);
        else if (playback.streamType.toUpperCase(Locale.ROOT).contains("HLS") || playback.streamType.toUpperCase(Locale.ROOT).contains("CMAF")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        if (!playback.license.isEmpty()) {
            item.setDrmConfiguration(new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setLicenseUri(playback.license).setForceDefaultLicenseUri(true).setMultiSession(true)
                .setLicenseRequestHeaders(Map.of("ascendonToken", playback.token, "entitlementToken", playback.entitlement, "x-f1-device-info", Protocol.TV_PROFILE))
                .build());
        }
        player.setMediaItem(item.build());
        if (resumePosition != C.TIME_UNSET) player.seekTo(resumePosition);
        player.prepare(); player.setPlayWhenReady(resumePlaying); surface.requestFocus(); updateStats();
    }
    private void failed(String message) {
        errorText = message; updateStats();
        new AlertDialog.Builder(this).setTitle("Stream could not start").setMessage(message)
            .setPositiveButton("Retry", (d, w) -> load()).setNeutralButton("Change route", (d, w) -> routeMenu())
            .setNegativeButton("Back", (d, w) -> finish()).show();
    }
    private void routeMenu() {
        String[] labels = new String[Protocol.Route.values().length];
        for (int i = 0; i < labels.length; i++) labels[i] = Protocol.Route.values()[i].label;
        new AlertDialog.Builder(this).setTitle("Playback route").setSingleChoiceItems(labels, route.ordinal(), (d, which) -> {
            if (player != null && !player.isCurrentMediaItemLive()) resumePosition = player.getCurrentPosition(); else resumePosition = C.TIME_UNSET;
            resumePlaying = player == null || player.getPlayWhenReady(); route = Protocol.Route.values()[which]; d.dismiss(); load();
        }).setNegativeButton("Cancel", null).show();
    }
    private void overlayVisibility() {
        stats.setVisibility(controlsVisible || statsPinned ? View.VISIBLE : View.GONE);
        updateStats();
    }
    private void playbackOptions() {
        String[] options = {"Match video frame rate to HDMI output", "Tunneled audio/video (experimental)"};
        boolean[] selected = {frameRateMatching, tunneling};
        new AlertDialog.Builder(this).setTitle("Playback options")
            .setMultiChoiceItems(options, selected, (d, which, enabled) -> selected[which] = enabled)
            .setPositiveButton("Apply", (d, which) -> {
                boolean restart = selected[1] != tunneling;
                frameRateMatching = selected[0]; tunneling = selected[1];
                getPreferences(0).edit().putBoolean("frameRateMatching", frameRateMatching).putBoolean("tunneling", tunneling).apply();
                if (restart) {
                    if (player != null) {
                        resumePosition = player.isCurrentMediaItemLive() ? C.TIME_UNSET : player.getCurrentPosition();
                        resumePlaying = player.getPlayWhenReady();
                    }
                    load();
                } else if (frameRateMatching && player != null && player.getVideoFormat() != null) {
                    Format format = player.getVideoFormat();
                    DeviceCapabilities.matchFrameRate(this, format.width, format.height, format.frameRate);
                } else if (!frameRateMatching) {
                    var params = getWindow().getAttributes(); params.preferredDisplayModeId = 0; getWindow().setAttributes(params);
                }
            }).setNegativeButton("Cancel", null).show();
    }
    private void tracks() {
        if (player == null) return;
        new AlertDialog.Builder(this).setTitle("Tracks").setItems(new String[]{"Video quality", "Audio language", "Subtitles"}, (d, which) ->
            selectTrack(which == 0 ? C.TRACK_TYPE_VIDEO : which == 1 ? C.TRACK_TYPE_AUDIO : C.TRACK_TYPE_TEXT))
            .setNegativeButton("Close", null).show();
    }
    private void selectTrack(int type) {
        if (player == null || selector == null) return;
        List<String> labels = new ArrayList<>(); List<Tracks.Group> groups = new ArrayList<>(); List<Integer> indexes = new ArrayList<>();
        labels.add(type == C.TRACK_TYPE_TEXT ? "Off" : "Automatic"); groups.add(null); indexes.add(-1);
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != type) continue;
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                Format f = group.getTrackFormat(i);
                String label = type == C.TRACK_TYPE_VIDEO ? f.width + "×" + f.height + " · " + bitrate(f.bitrate) :
                    (f.label == null ? f.language == null ? "Track " + (i + 1) : f.language : f.label);
                labels.add(label + (group.isTrackSelected(i) ? " ✓" : "")); groups.add(group); indexes.add(i);
            }
        }
        new AlertDialog.Builder(this).setTitle("Select track").setItems(labels.toArray(new String[0]), (d, which) -> {
            if (selector == null) return;
            var params = selector.buildUponParameters().clearOverridesOfType(type).setTrackTypeDisabled(type, type == C.TRACK_TYPE_TEXT && which == 0);
            if (groups.get(which) != null) params.addOverride(new TrackSelectionOverride(groups.get(which).getMediaTrackGroup(), indexes.get(which)));
            selector.setParameters(params);
        }).setNegativeButton("Cancel", null).show();
    }
    private static String bitrate(int bits) { return bits > 0 ? String.format(Locale.US, "%.1f Mb/s", bits / 1_000_000.0) : "bitrate pending"; }
    private void updateStats() {
        // Do not re-layout the large text overlay while the viewer is watching video.
        if (stats == null || stats.getVisibility() != View.VISIBLE) return;
        Format f = player == null ? null : player.getVideoFormat();
        var counters = player == null ? null : player.getVideoDecoderCounters();
        if (counters != null) counters.ensureUpdated();
        int frames = counters == null ? 0 : counters.renderedOutputBufferCount;
        String range = f == null || f.colorInfo == null ? "range unknown" :
            f.colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG ? "HLG source" : f.colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 ? "PQ source" : "SDR source";
        String resolution = frameRendered ? width + "×" + height + " rendered" : "Waiting for rendered video";
        String verdict = Protocol.renderedUhd(width, height, Math.max(0, frames - framesAtSizeChange)) ? "2160p frames rendered" : "2160p not verified";
        String phase = player == null ? "Preparing" : player.isPlaying() ? "Playing" : player.getPlaybackState() == Player.STATE_BUFFERING ? "Buffering" : "Paused / waiting";
        stats.setText(title + "\n" + route.label + " · " + sourceType + "\n" + resolution + " · " + range + "\n" +
            (f == null ? "Codec pending" : f.sampleMimeType + " · " + bitrate(f.bitrate) + " · " + String.format(Locale.US, "%.0f fps", f.frameRate)) + "\n" +
            "Manifest ceiling: " + (maxHeight > 0 ? maxHeight + "p" : "pending") + " · Device DRM: " + drmLevel + "\n" +
            "Decoder: " + decoder + "\n" + DeviceCapabilities.outputSummary(this, f == null ? -1 : f.frameRate) +
            "\n" + recentFrames.summary() + "\n" +
            "Buffer: " + String.format(Locale.US, "%.1f s", player == null ? 0 : Math.max(0, player.getBufferedPosition() - player.getCurrentPosition()) / 1000.0) +
            " · Buffering events: " + bufferingEvents + "\n" +
            "Tunneling: " + (tunneling ? "requested" : "off") + " · " + phase +
            "\nTotal frames: " + frames + " · Dropped: " + (counters == null ? 0 : counters.droppedBufferCount) +
            "\n" + verdict + (errorText.isEmpty() ? "" : "\n" + errorText));
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            surface.showController(); actions.setVisibility(View.VISIBLE); actions.getChildAt(0).requestFocus(); return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && player != null) {
            if (player.isPlaying()) player.pause(); else player.play(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    private void back() {
        if (surface != null && surface.isControllerFullyVisible()) { surface.hideController(); surface.requestFocus(); }
        else finish();
    }
    private void releasePlayer() {
        if (player != null) { surface.setPlayer(null); player.release(); player = null; selector = null; }
    }
    @Override protected void onStop() {
        stopped = true; generation++; main.removeCallbacks(tick);
        if (player != null) { resumePlaying = player.getPlayWhenReady(); resumePosition = player.isCurrentMediaItemLive() ? C.TIME_UNSET : player.getCurrentPosition(); }
        releasePlayer(); super.onStop();
    }
    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy(); }
}

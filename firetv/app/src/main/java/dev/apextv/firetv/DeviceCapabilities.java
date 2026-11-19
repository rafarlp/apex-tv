package dev.apextv.firetv;

import android.app.Activity;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaDrm;
import android.os.Build;
import android.view.Display;
import java.util.Arrays;
import java.util.UUID;

final class DeviceCapabilities {
    static final UUID WIDEVINE = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed");
    static String drmLevel() {
        try (MediaDrm drm = new MediaDrm(WIDEVINE)) { return drm.getPropertyString("securityLevel"); }
        catch (Exception e) { return "unavailable"; }
    }
    static String describe(Activity activity) {
        StringBuilder info = new StringBuilder(Build.MANUFACTURER + " " + Build.MODEL + "\nAndroid API " + Build.VERSION.SDK_INT +
            " · ABI " + String.join(", ", Build.SUPPORTED_ABIS) + "\nDevice Widevine: " + drmLevel() + "\n");
        Display display = activity.getWindowManager().getDefaultDisplay();
        if (display != null) {
            Display.Mode mode = display.getMode();
            info.append("Output: ").append(mode.getPhysicalWidth()).append('×').append(mode.getPhysicalHeight()).append(" @ ").append(Math.round(mode.getRefreshRate())).append(" Hz\n");
            info.append("HDR types: ").append(Arrays.toString(display.getHdrCapabilities().getSupportedHdrTypes())).append(" (2=HDR10, 3=HLG)\n");
        }
        info.append("\nHEVC decoders reporting 3840×2160 @ 50fps:\n");
        int count = 0;
        try {
            for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
                if (codec.isEncoder()) continue;
                for (String type : codec.getSupportedTypes()) {
                    if (!"video/hevc".equalsIgnoreCase(type)) continue;
                    MediaCodecInfo.CodecCapabilities caps = codec.getCapabilitiesForType(type);
                    if (caps.getVideoCapabilities() != null && caps.getVideoCapabilities().areSizeAndRateSupported(3840, 2160, 50)) {
                        info.append(codec.getName()).append(caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback) ? " · secure" : "").append('\n'); count++;
                    }
                }
            }
        } catch (RuntimeException e) { info.append("Capability query unavailable\n"); }
        if (count == 0) info.append("No decoder advertised this mode\n");
        info.append("\nCapabilities do not prove stream authorization or HDR picture accuracy.");
        return info.toString();
    }
    static void preferUhdOutput(Activity activity) {
        Display display = activity.getWindowManager().getDefaultDisplay();
        if (display == null) return;
        Display.Mode selected = null;
        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() < 3840 || mode.getPhysicalHeight() < 2160) continue;
            if (selected == null || Math.abs(mode.getRefreshRate() - 50) < Math.abs(selected.getRefreshRate() - 50)) selected = mode;
        }
        if (selected != null) {
            var params = activity.getWindow().getAttributes(); params.preferredDisplayModeId = selected.getModeId(); activity.getWindow().setAttributes(params);
        }
    }
    private DeviceCapabilities() {}
}

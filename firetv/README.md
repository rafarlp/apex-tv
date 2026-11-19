# Apex TV — Fire TV experiment

A native, sideloadable F1 TV client targeting **Fire TV Stick 4K Max 1st Gen
(2021), model AFTKA**, Fire OS 7 / Android API 28. The APK has no native ABI
dependency, no Google Play Services dependency, and a separate package ID
(`dev.apextv.firetv`). It can coexist with the official F1 TV app.

This is a new Android client informed by Vroom's working native playback route,
not a binary port of its Swift/AVFoundation code. See [NOTICE.md](NOTICE.md).

## First test

1. Enable **ADB debugging** under Settings → My Fire TV → Developer Options.
   If Developer Options is hidden, select the device name under About seven times.
2. Read the local IP under **About → Network**. Keep Mac and Fire Stick on the
   same network. Run from this workspace:

   ```sh
   bash scripts/install-firetv.sh 192.168.1.123
   ```

   Replace the example address with the actual Fire Stick address. Approve the
   computer's debugging prompt on the TV if requested, then repeat the command.
   The script installs only Apex TV and opens it.
3. Open **Account**, scan the QR code, and sign in directly on F1's account site
   from your phone or computer. Tokens are encrypted using Android Keystore;
   the app never asks for or stores your F1 password. Premium is required for 4K.
4. Choose **Browse → current season → race weekend → session → feed**. You can
   also enter an F1 TV session link or content ID in **Open session**.
5. Start with **UHD / HDR · DASH** on an HDR-capable 4K display. The player keeps
   native Widevine, secure decoding and output protection in place. It requests
   the TV stream with an Android TV playback profile. This request profile does
   not create a different DRM device or guarantee a license.

## Verify the actual result

The overlay shows rendered video dimensions, source HDR transfer function,
selected format bitrate/frame rate, maximum manifest height, decoder name,
rendered/dropped-frame counters, and device-reported Widevine security level.

Success means **3840×2160 rendered**, a rising frame count, and a stable picture
and audio. The overlay says **2160p frames rendered** only after UHD dimensions
and a positive rendered-frame count. This is decoder telemetry; it cannot detect
a green/corrupt image or prove accurate HDR output. Inspect the actual picture.
HLG/PQ in the overlay describes the source, not a measured panel HDR output.

Test a recent replay first, then repeat with an actually live F1 session. Live
and replay use the same UHD request policy; no hard-coded live 1080p fallback is
applied. A replay test does not establish live compatibility.

**Menu** opens the top playback buttons. **Tracks** selects video quality, audio,
or subtitles; **Route** retries with UHD HLS, TV automatic, or HD compatibility;
**Live** returns to the live edge; **Pin stats** keeps the overlay visible after
the controls hide. Hidden statistics do not redraw. **Playback** configures
frame-rate matching and optional tunneled audio/video. Normal media
buttons and D-pad player controls remain available. Back hides controls first,
then returns to browsing.

## Video stutter

F1 TV's 50 fps broadcast is expected. For even cadence, the HDMI output should be
50 Hz (or a multiple such as 100 Hz). A TV advertised as 60 Hz may also accept a
50 Hz HDMI input; inspect **Device → Available output modes** or the current HDMI
line in playback stats. If no compatible UHD cadence exists, the app retains UHD
and reports the uneven cadence instead of silently reducing output resolution.

The overlay now shows recent renderer throughput/drop fraction, buffer length,
and buffering events. Renderer drops, source frame rate, and HDMI cadence are
distinct measurements. An uneven HDMI cadence may cause judder even without
renderer drops. The stats cannot measure the TV's internal panel processing.

First let playback controls/stats hide. If video still stutters while audio stays
smooth, try **Playback → Tunneled audio/video** and compare the same replay segment.
This option restarts playback at the current replay position; it is off by default
because platform/codec support differs. Its label says requested rather than
claiming the hardware actually entered tunneling. Disable it if picture or audio
gets worse. Automatic quality is adaptive; manually choose 2160p for a fixed-UHD
comparison if needed.

## Build

Requires an Apple Silicon Mac and a JDK compatible with Gradle 9.5.1 (JDK 25
was used locally). Build caches, SDK packages and signing key stay in `.build/`.

```sh
bash scripts/setup-firetv-sdk.sh  # first setup; review the Android SDK license
bash scripts/build-firetv.sh
```

The build runs JVM tests, Android lint, and release assembly, then verifies the
APK signature and writes **build/ApexTV-firetv.apk**. The APK is signed with the
project-local development keystore, not an official developer certificate.
Retain `.build/apex-tv.keystore` to install updates over this copy without losing
app data. Do not publish the keystore or account tokens.

## Limits and next evidence

- A first AFTKA device test reports successful pairing and replay playback. Its
  photo shows 3840×2160, HEVC at 50 fps / 15.4 Mb/s, HLG source signaling, and the
  secure MediaTek hardware decoder. Some dropped video frames are reported.
  These findings do not establish accurate panel HDR output or live playback.
- A returned 2160p manifest is separate from a usable Widevine license, secure
  HEVC decoding, and correct HLG presentation. Device identification alone may
  not resolve all three.
- No DRM keys are extracted, and no account entitlements are fabricated.
- First-generation Fire OS uses 32-bit apps. Do not infer that it can run a
  patched app's arm64-only ClearVR path; Apex uses the platform MediaCodec path.
- No multiview, custom radio, update service, or playback recording is included.

For a failed test, retain the playback route, error-code name, rendered size,
decoder name, source range, and frame counters visible in the UI. Do not share
account tokens, signed URLs, or pairing codes.

## Validation — 2026-11-18

- Release APK assembled and APK v2 signature verification passed.
- 10 JVM tests passed: catalog traversal, URL origin restrictions, pairing URL
  validation, input validation, rendering verdicts, token renewal and expiration.
- Android lint passed with no errors. Warnings remain for dependency versions,
  English-only text and TV-specific presentation.
- APK metadata reports minimum API 28; it contains no native `.so` libraries.
- The public F1 catalog responded successfully. Device authorization returned
  HTTP 200, `https://f1.com/pair`, a 600-second lifetime and a 5-second poll
  interval. No account was signed in during that check; codes were not retained.
- A subsequent user device test reports installation, account pairing, responsive
  remote interaction and UHD replay playback. Its photo shows renderer telemetry,
  not an independent panel HDR measurement. Live playback remains unverified.

Version 0.2.0 builds and passes 17 JVM tests, Android lint, and APK signature
verification. Its timing, overlay, adaptive-selection and tunneling changes still
need a physical AFTKA comparison. See [RELEASE.md](RELEASE.md).

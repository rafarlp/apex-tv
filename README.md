# Apex TV

An experimental native F1 TV client for **Fire TV Stick 4K Max 1st Gen (2021)**,
model AFTKA, running Fire OS 7 / Android 9.

[Download the APK](https://github.com/rafarlp/apex-tv/releases/download/v0.1.0/ApexTV-firetv-0.1.0.apk)
or enter **3638576** in Downloader. Keep the same code for preview updates.
The legacy APK filename stays fixed; the version inside is currently **0.2.0**.
See the [rolling preview](https://github.com/rafarlp/apex-tv/releases/tag/v0.1.0)
for current source/checksums, or the [versioned release history](https://github.com/rafarlp/apex-tv/releases).

## What it does

- Browse the F1 TV catalog with a remote and select main feeds or onboard cameras.
- Pair an F1 TV account using a QR code; credentials stay encrypted on the device.
- Request TV UHD/HDR streams through native Media3 and Widevine playback.
- Inspect rendered dimensions, frame counts, decoder, and source HDR signaling.

Requires your own F1 TV subscription, with Premium for 4K. A first AFTKA device
test reports successful 3840×2160 replay playback with secure hardware HEVC and
an HLG source, alongside some dropped video frames. **Live 4K and accurate panel
HDR output remain unverified.** Version 0.2.0 adds frame-rate matching, a less
intrusive overlay, recent playback measurements, and optional A/V tunneling.
Its performance changes still need a device comparison.

See [the Fire TV guide](firetv/README.md) for installation, controls, and testing.

## Build

From this directory on an Apple Silicon Mac with a compatible JDK:

```sh
bash scripts/setup-firetv-sdk.sh
bash scripts/build-firetv.sh
```

The build runs 17 JVM tests, Android lint, release assembly, and signature
verification. SDK packages, caches, and the local signing key stay in `.build/`.

## Source and credits

This is a separate Android client informed by Vroom TV and community F1 TV
protocol research. It is not an official Vroom port or an official Formula 1
product. See [NOTICE](firetv/NOTICE.md) and [GPL-3.0 license](firetv/LICENSE).

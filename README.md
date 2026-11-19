# Apex TV

An experimental native F1 TV client for **Fire TV Stick 4K Max 1st Gen (2021)**,
model AFTKA, running Fire OS 7 / Android 9.

[Download the APK](https://github.com/rafarlp/apex-tv/releases/download/v0.1.0/ApexTV-firetv-0.1.0.apk)
or open the [preview release](https://github.com/rafarlp/apex-tv/releases/tag/v0.1.0)
for source and checksums.

## What it does

- Browse the F1 TV catalog with a remote and select main feeds or onboard cameras.
- Pair an F1 TV account using a QR code; credentials stay encrypted on the device.
- Request TV UHD/HDR streams through native Media3 and Widevine playback.
- Inspect rendered dimensions, frame counts, decoder, and source HDR signaling.

Requires your own F1 TV subscription, with Premium for 4K. **Physical Fire Stick
playback, live 4K, and correct HDR output have not been verified.** This first
build is for testing compatibility, not a guarantee that UHD works on AFTKA.

See [the Fire TV guide](firetv/README.md) for installation, controls, and testing.

## Build

From this directory on an Apple Silicon Mac with a compatible JDK:

```sh
bash scripts/setup-firetv-sdk.sh
bash scripts/build-firetv.sh
```

The build runs 10 JVM tests, Android lint, release assembly, and signature
verification. SDK packages, caches, and the local signing key stay in `.build/`.

## Source and credits

This is a separate Android client informed by Vroom TV and community F1 TV
protocol research. It is not an official Vroom port or an official Formula 1
product. See [NOTICE](firetv/NOTICE.md) and [GPL-3.0 license](firetv/LICENSE).

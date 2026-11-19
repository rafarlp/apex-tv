# Apex TV

A separate experimental Android TV / Fire OS F1 TV client, created in this
workspace. It is not an official Vroom TV port and is not associated with
Formula 1, Amazon, Vroom TV, or Race Control TV.

The protocol investigation benefited from:

- Noah Fetz's [Vroom TV](https://github.com/NoahFetz/F1AppleTV), checkout
  `7cc47a3c94b382db5a3fef1e2492779b6f6cc916`: device authorization grant,
  playback session lifetime, catalog envelopes, and feed metadata.
- [st14n/race-control-tv](https://github.com/st14n/race-control-tv), checkout
  `91fc8fb`: Android TV request profile and native Widevine request headers.
- [Alexvbp/f1tv-4k-patch](https://github.com/Alexvbp/f1tv-4k-patch): compatibility
  research only. No patched proprietary APK or native code is included.

The app uses AndroidX Media3 (Apache-2.0), AndroidX Activity (Apache-2.0),
ZXing Core (Apache-2.0), and Android core library desugaring (their respective
upstream licenses apply). Gradle's wrapper is generated from official Gradle
9.5.1; its checksum matches the published Gradle wrapper checksum.

The new application sources are provided under GPL-3.0-or-later. See LICENSE.

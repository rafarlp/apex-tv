# Apex TV 0.2.0 — playback timing and diagnostics

The first device test reports successful account pairing and 3840×2160 replay
playback on Fire TV Stick 4K Max 1st Gen, using its secure MediaTek HEVC decoder
with an HLG source. Some video frames are dropped; smooth performance remains
under investigation. Correct panel HDR output and live playback are not verified.

Changes in this preview:

- Hide the stats overlay with playback controls and stop redrawing hidden text.
  Use **Pin stats** when measuring performance.
- Match the HDMI output to the actual video's frame rate where the device reports
  a compatible mode. Preserve UHD resolution and show the actual HDMI mode in
  stats. A 60 Hz-only output is explicitly labeled as uneven cadence for 50 fps.
- Show supported HDMI modes under **Device** so 50 Hz capability can be checked.
- Add a recent 10-second renderer frame-rate/drop reading, buffered seconds, and
  buffering-event counts. Old startup drops no longer dominate the recent result.
- Add **Playback → Tunneled audio/video (experimental)**, off by default, for a
  hardware A/V synchronization comparison. Media3 uses it only for supported
  audio/video tracks. Disable it again if the device behaves worse.
- Restore bandwidth-aware selection for **Automatic** video quality. For a fixed
  2160p comparison, select the 2160p track manually under **Tracks → Video quality**.

The APK uses the same package and signing key as 0.1.0, so it can be installed as
an update. No need to uninstall or pair again unless the F1 session has expired.

Build, signature verification, 17 JVM tests, and Android lint are checked locally.
These playback changes have not yet been measured on the physical Fire Stick.

Requires your own F1 TV subscription, with Premium for 4K. Source and checksums
are included. Not affiliated with Formula 1, Amazon, or Vroom TV.

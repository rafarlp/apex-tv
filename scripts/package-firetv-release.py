#!/usr/bin/env python3
"""Prepare a public release from an explicit source allowlist; never package build caches or keys."""
from pathlib import Path
import hashlib
import shutil
import zipfile
import re

root = Path(__file__).resolve().parent.parent
version = re.search(r"versionName\s+'([^']+)'", (root / "firetv/app/build.gradle").read_text()).group(1)
destination = root / "build" / "firetv-release" / version
destination.mkdir(parents=True, exist_ok=True)
apk = destination / f"ApexTV-firetv-{version}.apk"
shutil.copy2(root / "build" / "ApexTV-firetv.apk", apk)
source_archive = destination / f"ApexTV-{version}-source.zip"

source_files = [
    "firetv/settings.gradle", "firetv/build.gradle", "firetv/gradle.properties",
    "firetv/.gitignore", "firetv/gradlew", "firetv/gradlew.bat",
    "firetv/gradle/wrapper/gradle-wrapper.jar",
    "firetv/gradle/wrapper/gradle-wrapper.properties", "firetv/app/build.gradle",
    "firetv/LICENSE", "firetv/NOTICE.md", "firetv/README.md", "firetv/RELEASE.md",
    "scripts/build-firetv.sh", "scripts/install-firetv.sh", "scripts/setup-firetv-sdk.sh",
    "scripts/package-firetv-release.py",
]
source_files += [str(p.relative_to(root)) for p in (root / "firetv/app/src").rglob("*") if p.is_file()]
with zipfile.ZipFile(source_archive, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    prefix = f"ApexTV-{version}/"
    for name in sorted(source_files):
        path = root / name
        if not path.is_file():
            raise FileNotFoundError(path)
        archive.write(path, prefix + name)
    archive.writestr(prefix + ".gitignore", ".build/\nbuild/\n**/build/\n**/.gradle/\n*.keystore\nlocal.properties\n")
    archive.writestr(prefix + "README.md", "# Apex TV\n\nExperimental F1 TV client for Fire TV Stick 4K Max 1st Gen.\n\n"
                     "See firetv/README.md for installation, building, and current verification limits.\n\n"
                     "Build from this directory with:\n\n```sh\nbash scripts/setup-firetv-sdk.sh\nbash scripts/build-firetv.sh\n```\n")

with (destination / "SHA256SUMS").open("w") as checksums:
    for path in [apk, source_archive]:
        checksums.write(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n")

shutil.copy2(root / "firetv/RELEASE.md", destination / "RELEASE.md")
with zipfile.ZipFile(source_archive) as archive:
    assert archive.testzip() is None
    assert not any("/.build/" in name or name.endswith(".keystore") or name.endswith("local.properties") for name in archive.namelist())
print(destination)
for path in [apk, source_archive, destination / "SHA256SUMS"]:
    print(f"{path.name}: {path.stat().st_size:,} bytes")

#!/usr/bin/env python3
"""Verifikasi kebijakan 100% luring (panduan/01-build-ui-sync.md).

Gagal (exit 1) bila ada izin jaringan atau dependensi jaringan/Firebase/Auth.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
GRADLE_FILES = [ROOT / "app" / "build.gradle.kts", ROOT / "gradle" / "libs.versions.toml"]

FORBIDDEN_PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
}
FORBIDDEN_DEPENDENCY_PATTERNS = [
    r"firebase",
    r"play-services-auth",
    r"google-services",
    r"okhttp",
    r"retrofit",
    r"ktor",
    r"volley",
]


def main() -> int:
    errors: list[str] = []

    manifest = MANIFEST.read_text(encoding="utf-8")
    for perm in re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', manifest):
        if perm in FORBIDDEN_PERMISSIONS:
            errors.append(f"Izin jaringan tidak boleh ada: {perm} ({MANIFEST.relative_to(ROOT)})")

    for gradle in GRADLE_FILES:
        for line in gradle.read_text(encoding="utf-8").lower().splitlines():
            stripped = line.strip()
            if stripped.startswith("//"):
                continue
            for pattern in FORBIDDEN_DEPENDENCY_PATTERNS:
                if re.search(pattern, stripped):
                    errors.append(f"Dependensi jaringan/Firebase/Auth: '{stripped}' ({gradle.relative_to(ROOT)})")
                    break

    if errors:
        print("Verifikasi luring GAGAL:")
        for e in errors:
            print(f"  - {e}")
        return 1

    print("Verifikasi luring OK: tanpa izin jaringan dan tanpa dependensi jaringan/Firebase/Auth.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

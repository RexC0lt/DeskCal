#!/usr/bin/env bash
# Build-check, tag and publish a GitHub release with the APK attached.
# Usage: ./scripts/make-release.sh v1.0.1
set -euo pipefail

TAG="${1:?usage: ./scripts/make-release.sh <tag> (e.g. v1.0.1)}"

if [ -n "${DESKCAL_KEYSTORE_PATH:-}" ] || [ -f keystore.properties ]; then
    APK="app/build/outputs/apk/release/app-release.apk"
else
    APK="app/build/outputs/apk/debug/app-debug.apk"
    echo "No release signing configured (DESKCAL_KEYSTORE_PATH / keystore.properties missing);"
    echo "attaching the debug APK instead. See README section 'Publishing a release on GitHub'."
fi

if [ ! -f "$APK" ]; then
    echo "APK not found: $APK"
    echo "Build it first with ./gradlew assembleRelease (or ./gradlew assembleDebug)."
    exit 1
fi

git tag "$TAG"
git push origin "$TAG"

gh release create "$TAG" "$APK" --title "DeskCal $TAG" --generate-notes

echo "Released: $(gh release view "$TAG" --json url --jq .url)"

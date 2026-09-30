#!/usr/bin/env bash
set -euo pipefail
BT=${BT:-$HOME/android-sdk/build-tools/35.0.0}; PLAT=${PLAT:-$HOME/android-sdk/platforms/android-35/android.jar}
d=$1; cd $(dirname "$0")/$d; rm -rf build; mkdir -p build/classes build/dex
$BT/aapt2 link -o build/u.apk -I $PLAT --manifest AndroidManifest.xml --min-sdk-version 24 --target-sdk-version 34
javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PLAT:$BT/core-lambda-stubs.jar" -d build/classes $(find src -name '*.java') 2>&1 | grep -v JAVA_TOOL || true
$BT/d8 --min-api 24 --lib $PLAT --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q -j ../u.apk classes.dex)
$BT/zipalign -f -p 4 build/u.apk build/a.apk
$BT/apksigner sign --ks ../../keystore/release.jks --ks-pass pass:${KS_PASS:-elderlocal} --ks-key-alias elder --out build/mock-$d.apk build/a.apk
ls -la build/mock-$d.apk

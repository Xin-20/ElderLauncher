#!/usr/bin/env bash
# 无需 Gradle 的最小构建：aapt2 → javac → d8 → zipalign → apksigner
set -euo pipefail
cd "$(dirname "$0")"
BT=${BT:-$HOME/android-sdk/build-tools/35.0.0}   # build-tools 目录（含 aapt2/d8/zipalign/apksigner/core-lambda-stubs.jar）
PLAT=${PLAT:-$HOME/android-sdk/platforms/android-35/android.jar}
OUT=build
rm -rf $OUT && mkdir -p $OUT/res $OUT/gen $OUT/classes $OUT/dex $OUT/test

echo "== aapt2 compile"
$BT/aapt2 compile --dir res -o $OUT/res/res.zip
echo "== aapt2 link"
$BT/aapt2 link -o $OUT/unsigned.apk -I "$PLAT" --manifest AndroidManifest.xml \
  --java $OUT/gen --min-sdk-version 24 --target-sdk-version 34 \
  --version-code ${VERSION_CODE:-8} --version-name ${VERSION_NAME:-1.7} $OUT/res/res.zip

echo "== javac"
javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PLAT:$BT/core-lambda-stubs.jar" \
  -d $OUT/classes $(find src $OUT/gen -name '*.java')

echo "== unit tests"
javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 -cp "$OUT/classes:$PLAT" -d $OUT/test $(find test -name '*.java')
java -cp "$OUT/classes:$OUT/test" com.elder.launcher.LogicTest

echo "== d8"
$BT/d8 --release --min-api 24 --lib "$PLAT" --output $OUT/dex $(find $OUT/classes -name '*.class')
cp $OUT/unsigned.apk $OUT/with-dex.apk
(cd $OUT/dex && zip -q -j ../with-dex.apk classes.dex)

echo "== zipalign + sign"
$BT/zipalign -f -p 4 $OUT/with-dex.apk $OUT/aligned.apk
KS=${KS:-keystore/release.jks}
KS_PASS=${KS_PASS:-elderlocal}   # 本地自动生成的签名密钥口令，可用环境变量覆盖
if [ ! -f "$KS" ]; then
  mkdir -p keystore
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias elder \
    -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=ElderLauncher, O=Family, C=CN" >/dev/null 2>&1
fi
$BT/apksigner sign --ks "$KS" --ks-pass pass:"$KS_PASS" --key-pass pass:"$KS_PASS" --ks-key-alias elder \
  --out $OUT/ElderLauncher.apk $OUT/aligned.apk
$BT/apksigner verify --verbose $OUT/ElderLauncher.apk | head -5
ls -la $OUT/ElderLauncher.apk

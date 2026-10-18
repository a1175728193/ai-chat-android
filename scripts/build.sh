#!/usr/bin/env bash
# 无 Gradle 的 Android 裸构建：aapt -> javac -> d8 -> zipalign -> apksigner
# 依赖环境变量：
#   ANDROID_JAR : android.jar 绝对路径
#   KEYSTORE    : keystore 绝对路径
#   KS_PASS     : keystore / key 密码
#   KS_ALIAS    : key 别名
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

: "${ANDROID_JAR:?未设置 ANDROID_JAR}"
: "${KEYSTORE:?未设置 KEYSTORE}"
: "${KS_PASS:?未设置 KS_PASS}"
: "${KS_ALIAS:?未设置 KS_ALIAS}"

APK_NAME="AI-Chat.apk"
PKG="$(sed -n 's/.*package="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)"
RPD="$(echo "$PKG" | tr '.' '/')"
echo "包名: $PKG    android.jar: $ANDROID_JAR"

rm -rf out
mkdir -p out/gen out/classes out/dex

echo "== 1/6 资源编译（aapt，生成 R.java）=="
aapt package -f -m --custom-package "$PKG" -J out/gen -M AndroidManifest.xml -S res -I "$ANDROID_JAR"
test -f "out/gen/$RPD/R.java" || { echo "!! R.java 未生成"; exit 1; }

echo "== 2/6 javac =="
javac -encoding UTF-8 -source 1.8 -target 1.8 -bootclasspath "$ANDROID_JAR" \
  -classpath "out/gen" -d out/classes \
  $(find src -name '*.java') "out/gen/$RPD/R.java" 2>&1 | grep -v 'bootstrap class path' || true
NCLASS="$(find out/classes -name '*.class' | wc -l)"
echo "  class 数: $NCLASS"
[ "$NCLASS" -gt 0 ] || { echo "!! javac 产物为空"; exit 1; }

echo "== 3/6 d8 -> classes.dex =="
find out/classes -name '*.class' > out/classes.rsp
d8 --release --lib "$ANDROID_JAR" --min-api 21 --output out/dex @out/classes.rsp
grep -aq "MainActivity" out/dex/classes.dex || { echo "!! dex 缺少 MainActivity"; exit 1; }
echo "  classes.dex $(stat -c%s out/dex/classes.dex) bytes"

echo "== 4/6 aapt 打包 APK =="
aapt package -f -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -F out/unsigned.apk
(cd out/dex && aapt add "$ROOT/out/unsigned.apk" classes.dex)

echo "== 5/6 zipalign =="
zipalign -f 4 out/unsigned.apk out/aligned.apk

echo "== 6/6 签名 =="
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" \
  --ks-key-alias "$KS_ALIAS" --key-pass "pass:$KS_PASS" \
  --out "$ROOT/$APK_NAME" out/aligned.apk

apksigner verify --print-certs "$ROOT/$APK_NAME" | head -4
aapt dump badging "$ROOT/$APK_NAME" | head -3
ls -la "$ROOT/$APK_NAME"
echo "构建完成：$ROOT/$APK_NAME"

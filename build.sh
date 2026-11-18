#!/bin/bash
# 闪链 P2P —— 完全本地构建脚本（零第三方依赖）
set -u
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP="$SCRIPT_DIR/app"
BUILD="$SCRIPT_DIR/build"
SRC="$APP/src/main/java"
RES="$APP/src/main/res"
MANIFEST="$APP/src/main/AndroidManifest.xml"
ANDROID_JAR="/usr/lib/android-sdk/platforms/android-23/android.jar"
D8_JAR="$SCRIPT_DIR/tools/r8.jar"
KEYSTORE="$SCRIPT_DIR/tools/debug.keystore"

echo "======================================"
echo "  闪链 P2P 本地构建 (B1 UDP打洞 + B4 IPv6直连)"
echo "======================================"
[ -f "$ANDROID_JAR" ] || { echo "缺少 android.jar"; exit 1; }
[ -f "$D8_JAR" ] || { echo "缺少 D8: $D8_JAR"; exit 1; }
command -v javac >/dev/null || { echo "缺少 javac"; exit 1; }
command -v apksigner >/dev/null || { echo "缺少 apksigner"; exit 1; }

echo "[1/7] 清理..."
rm -rf "$BUILD"; mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/gen" "$BUILD/apk"

echo "[2/7] 签名密钥..."
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -v -keystore "$KEYSTORE" -alias flashlink -keyalg RSA \
      -keysize 2048 -validity 10000 -storepass android -keypass android \
      -dname "CN=FlashLink, OU=Dev, O=FlashLink, L=NA, S=NA, C=CN" >/dev/null 2>&1
    echo "  已生成"
fi

echo "[3/7] 资源 (aapt)..."
aapt package -f -m -J "$BUILD/gen" -M "$MANIFEST" -S "$RES" \
    -I "$ANDROID_JAR" -P "$BUILD/public.txt" \
    --min-sdk-version 21 --target-sdk-version 34
[ $? -eq 0 ] || { echo "资源失败"; exit 1; }

echo "[4/7] Java (javac)..."
find "$SRC" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 \
    -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
    -d "$BUILD/classes" @"$BUILD/sources.txt"
[ $? -eq 0 ] || { echo "Java编译失败"; exit 1; }

echo "[5/7] DEX (d8)..."
CLASS_FILES=$(find "$BUILD/classes" -name '*.class' | tr '\n' ' ')
java -cp "$D8_JAR" com.android.tools.r8.D8 \
    --lib "$ANDROID_JAR" --min-api 21 --output "$BUILD/dex" $CLASS_FILES
[ $? -eq 0 ] || { echo "DEX失败"; exit 1; }

echo "[6/7] 打包..."
cp "$BUILD/dex/classes.dex" "$BUILD/apk/"
aapt package -f -M "$MANIFEST" -S "$RES" -I "$ANDROID_JAR" \
    -F "$BUILD/apk/unsigned.apk" --min-sdk-version 21 --target-sdk-version 34
[ $? -eq 0 ] || { echo "打包失败"; exit 1; }
cd "$BUILD/apk" && aapt add unsigned.apk classes.dex >/dev/null 2>&1
cd "$SCRIPT_DIR"

echo "[7/7] 对齐签名..."
zipalign -f -p 4 "$BUILD/apk/unsigned.apk" "$BUILD/apk/aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --out "$BUILD/apk/FlashLink-P2P.apk" "$BUILD/apk/aligned.apk"
apksigner verify "$BUILD/apk/FlashLink-P2P.apk" >/dev/null && echo "  签名验证通过"
cp "$BUILD/apk/FlashLink-P2P.apk" "$SCRIPT_DIR/FlashLink-P2P.apk"
echo ""
echo "======================================"
echo "  构建成功: $SCRIPT_DIR/FlashLink-P2P.apk"
echo "  大小: $(du -h "$SCRIPT_DIR/FlashLink-P2P.apk" | cut -f1)"
echo "======================================"
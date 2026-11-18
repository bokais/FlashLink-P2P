#!/bin/bash
# 闪链 P2P —— 下载构建依赖（D8 编译器）
# 用法: bash fetch-tools.sh
set -u
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$SCRIPT_DIR/tools"
if [ -f "$SCRIPT_DIR/tools/r8.jar" ]; then
    echo "D8 已存在，无需下载"
    exit 0
fi
echo "下载 D8 编译器 (约15MB)..."
curl -fL --retry 3 --max-time 180 \
    -o "$SCRIPT_DIR/tools/r8.jar" \
    "https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.2.47/r8-8.2.47.jar"
if [ $? -eq 0 ]; then
    echo "下载完成: $SCRIPT_DIR/tools/r8.jar"
    java -cp "$SCRIPT_DIR/tools/r8.jar" com.android.tools.r8.D8 --version 2>&1 | head -1
else
    echo "下载失败，请手动下载并放到 tools/r8.jar"
    exit 1
fi
#!/usr/bin/env bash
# Monster Waves 部署脚本（在项目根目录用 bash 运行：./deploy.sh 或 bash deploy.sh）
# 1. 备份 mods 中现有的 monsterwaves jar 到 mods_backup（前一版，保留 .previous.jar）
# 2. 复制最新构建产物（mod 本体）到测试环境 mods 目录
set -euo pipefail

SRC="build/libs/monsterwaves-1.0.0.jar"
MODS="D:/game/mc/.minecraft/versions/测试MOD/mods"
BACKUP="D:/game/mc/.minecraft/versions/测试MOD/mods_backup"

[ -f "$SRC" ] || { echo "[错误] 未找到构建产物: $SRC （请先运行 gradlew.bat build）"; exit 1; }
[ -d "$MODS" ] || { echo "[错误] 目标 mods 目录不存在: $MODS"; exit 1; }
mkdir -p "$BACKUP"

# 备份前一版
backed=0
for f in "$MODS"/monsterwaves-*.jar; do
    if [ -f "$f" ]; then
        name="$(basename "$f" .jar).previous.jar"
        cp "$f" "$BACKUP/$name"
        echo "[备份] $(basename "$f") -> $BACKUP/$name"
        rm -f "$f"
        backed=1
    fi
done
[ "$backed" -eq 1 ] || echo "[提示] mods 中无旧版 monsterwaves jar，跳过备份"

# 复制新版（只复制 mod 本体 jar，不含日志等其他文件）
cp "$SRC" "$MODS/"
echo "[完成] 已部署 monsterwaves-1.0.0.jar -> $MODS/"

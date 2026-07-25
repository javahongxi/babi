#!/usr/bin/env bash
set -euo pipefail

# ─── Babi Agent CLI (Java) 卸载脚本 ───

INSTALL_DIR="$HOME/.babi"
BIN_DIR="$INSTALL_DIR/bin"
JAR_NAME="babi-agent-cli.jar"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }

# ─── 删除 Java jar ───
JAR_FILE="$INSTALL_DIR/$JAR_NAME"
if [ -f "$JAR_FILE" ]; then
    info "删除 $JAR_FILE ..."
    rm -f "$JAR_FILE"
    ok "已删除 $JAR_FILE"
else
    warn "$JAR_FILE 不存在，跳过"
fi

# ─── 删除 Java 启动脚本 ───
LAUNCHER="$BIN_DIR/babi"
if [ -f "$LAUNCHER" ]; then
    info "删除启动脚本 $LAUNCHER ..."
    rm -f "$LAUNCHER"
    ok "已删除 $LAUNCHER"
else
    warn "启动脚本 $LAUNCHER 不存在，跳过"
fi

# ─── 清理空的 bin 目录（如果已无其他 babi 启动器）───
if [ -d "$BIN_DIR" ]; then
    remaining=$(ls -A "$BIN_DIR" 2>/dev/null | grep -c "^babi" || true)
    if [ "$remaining" -eq 0 ]; then
        info "清理空的 bin 目录 $BIN_DIR ..."
        rm -rf "$BIN_DIR"
        ok "已删除 $BIN_DIR"
    else
        ok "bin 目录中还有其他 babi 启动器（$remaining 个），保留 $BIN_DIR"
    fi
fi

# ─── 清理空的安装目录 ───
if [ -d "$INSTALL_DIR" ] && [ -z "$(ls -A "$INSTALL_DIR" 2>/dev/null)" ]; then
    info "清理空目录 $INSTALL_DIR ..."
    rm -rf "$INSTALL_DIR"
    ok "已删除 $INSTALL_DIR"
fi

# ─── 清理 shell 配置（仅当 ~/.babi 已不存在时）───
if [ ! -d "$INSTALL_DIR" ]; then
    SHELL_RC=""
    if [ -n "${ZSH_VERSION:-}" ] || [ "$(basename "$SHELL" 2>/dev/null)" = "zsh" ]; then
        SHELL_RC="$HOME/.zshrc"
    elif [ -n "${BASH_VERSION:-}" ]; then
        if [ -f "$HOME/.bash_profile" ]; then
            SHELL_RC="$HOME/.bash_profile"
        else
            SHELL_RC="$HOME/.bashrc"
        fi
    fi

    if [ -n "${SHELL_RC:-}" ] && [ -f "${SHELL_RC:-}" ]; then
        if grep -qF "$BIN_DIR" "${SHELL_RC}" 2>/dev/null; then
            info "清理 $SHELL_RC 中的 Babi Agent 配置..."
            sed -i '' "/# Babi Agent CLI/d" "$SHELL_RC" 2>/dev/null || true
            sed -i '' "/export PATH=\".*\\.babi\\/bin/d" "$SHELL_RC" 2>/dev/null || true
            ok "已清理 $SHELL_RC"
            warn "请执行 source $SHELL_RC 使更改生效"
        else
            ok "shell 配置中未发现 Babi Agent 相关内容"
        fi
    fi
fi

echo ""
echo "═══════════════════════════════════════════════════"
echo -e "  ${GREEN}Babi Agent CLI (Java) 已卸载${NC}"
echo "═══════════════════════════════════════════════════"
echo ""

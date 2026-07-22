#!/usr/bin/env bash
set -euo pipefail

# ─── Babi Agent CLI 卸载脚本 ───

INSTALL_DIR="$HOME/.babi"
BIN_DIR="$INSTALL_DIR/bin"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }

# ─── 删除安装目录 ───
if [ -d "$INSTALL_DIR" ]; then
    info "删除安装目录 $INSTALL_DIR ..."
    rm -rf "$INSTALL_DIR"
    ok "已删除 $INSTALL_DIR"
else
    warn "安装目录 $INSTALL_DIR 不存在，跳过"
fi

# ─── 清理 shell 配置 ───
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

if [ -n "$SHELL_RC" ] && [ -f "$SHELL_RC" ]; then
    if grep -qF "# Babi Agent CLI" "$SHELL_RC" 2>/dev/null; then
        info "清理 $SHELL_RC 中的 Babi Agent 配置..."
        # 删除 "# Babi Agent CLI" 注释行及其下一行的 PATH 配置
        sed -i '' '/# Babi Agent CLI/d' "$SHELL_RC"
        sed -i '' "/export PATH=\".*\.babi\/bin/d" "$SHELL_RC"
        ok "已清理 $SHELL_RC"
        warn "请执行 source $SHELL_RC 使更改生效"
    else
        ok "shell 配置中未发现 Babi Agent 相关内容"
    fi
fi

echo ""
echo "═══════════════════════════════════════════════════"
echo -e "  ${GREEN}Babi Agent CLI 已卸载${NC}"
echo "═══════════════════════════════════════════════════"
echo ""

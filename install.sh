#!/usr/bin/env bash
set -euo pipefail

# ─── Babi Agent CLI 一键安装脚本 ───
# 构建项目 → 安装 CLI jar → 创建启动脚本 → 配置环境变量
# 安装完成后，在终端输入 babi 即可启动

INSTALL_DIR="$HOME/.babi"
BIN_DIR="$INSTALL_DIR/bin"
JAR_NAME="babi-agent-cli.jar"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()   { echo -e "${RED}[ERROR]${NC} $*"; }

# ─── 前置检查 ───
info "检查环境依赖..."

# 检查 Java
if ! command -v java &>/dev/null; then
    err "未找到 java，请先安装 JDK 17+"
    err "  macOS:  brew install openjdk@17"
    err "  Ubuntu: sudo apt install openjdk-17-jdk"
    exit 1
fi
JAVA_VER=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
ok "Java $JAVA_VER"

# 检查 Maven
if ! command -v mvn &>/dev/null; then
    err "未找到 mvn，请先安装 Maven"
    err "  macOS:  brew install maven"
    err "  Ubuntu: sudo apt install maven"
    exit 1
fi
MVN_VER=$(mvn --version 2>&1 | head -1 | awk '{print $3}')
ok "Maven $MVN_VER"

# ─── 构建项目 ───
info "构建 CLI 专用 jar（首次构建可能需要几分钟）..."
cd "$SCRIPT_DIR"
mvn package -Pcli -DskipTests -q 2>&1 | tail -5

CLI_JAR="$SCRIPT_DIR/babi-agent/target/$JAR_NAME"
if [ ! -f "$CLI_JAR" ]; then
    err "构建失败：未找到 $CLI_JAR"
    exit 1
fi
ok "构建成功"

# ─── 安装文件 ───
info "安装到 $INSTALL_DIR ..."
mkdir -p "$BIN_DIR"
cp "$CLI_JAR" "$INSTALL_DIR/$JAR_NAME"
ok "已安装 $JAR_NAME"

# ─── 创建启动脚本 ───
cat > "$BIN_DIR/babi" << 'LAUNCHER'
#!/usr/bin/env bash
# Babi Agent CLI 启动器
# 支持透传所有参数给 Java 主类

INSTALL_DIR="$HOME/.babi"
JAR="$INSTALL_DIR/babi-agent-cli.jar"

if [ ! -f "$JAR" ]; then
    echo "Error: Babi Agent 未安装或 jar 文件丢失"
    echo "请重新运行 install.sh 进行安装"
    exit 1
fi

exec java -jar "$JAR" "$@"
LAUNCHER
chmod +x "$BIN_DIR/babi"
ok "已创建启动脚本 $BIN_DIR/babi"

# ─── 配置环境变量 ───
SHELL_RC=""
if [ -n "${ZSH_VERSION:-}" ] || [ "$(basename "$SHELL")" = "zsh" ]; then
    SHELL_RC="$HOME/.zshrc"
elif [ -n "${BASH_VERSION:-}" ]; then
    if [ -f "$HOME/.bash_profile" ]; then
        SHELL_RC="$HOME/.bash_profile"
    else
        SHELL_RC="$HOME/.bashrc"
    fi
elif [ -n "${FISH_VERSION:-}" ]; then
    SHELL_RC="$HOME/.config/fish/config.fish"
fi

PATH_LINE="export PATH=\"$BIN_DIR:\$PATH\""

if [ -n "${SHELL_RC:-}" ] && [ -f "${SHELL_RC:-}" ]; then
    if ! grep -qF "$BIN_DIR" "${SHELL_RC}" 2>/dev/null; then
        echo "" >> "${SHELL_RC}"
        echo "# Babi Agent CLI" >> "${SHELL_RC}"
        echo "$PATH_LINE" >> "${SHELL_RC}"
        ok "已将 $BIN_DIR 添加到 PATH（写入 ${SHELL_RC}）"
    else
        ok "PATH 已配置，跳过"
    fi
else
    warn "未检测到 shell 配置文件，请手动将以下内容添加到你的 shell 配置中："
    echo "  $PATH_LINE"
fi

# ─── 完成 ───
echo ""
echo "═══════════════════════════════════════════════════"
echo -e "  ${GREEN}Babi Agent CLI 安装完成！${NC}"
echo "═══════════════════════════════════════════════════"
echo ""
echo "  使用方式："
if [ -n "${SHELL_RC:-}" ]; then
echo "    1. 重新加载 shell 配置："
echo "       source ${SHELL_RC}"
else
echo "    1. 打开新终端窗口"
fi
echo "    2. 设置 API Key（如未设置）："
echo "       export DASHSCOPE_API_KEY=your_api_key"
echo "    3. 启动 Babi Agent："
echo "       babi"
echo "    4. 指定工作区："
echo "       babi --workspace ~/my-project"
echo ""
echo "  常用命令："
echo "    babi --workspace .   # 在当前目录启动"
echo ""
echo "  卸载：运行 uninstall.sh 或手动删除 ~/.babi"
echo ""

---
name: java-dev-env-setup
description: 在新 Mac 上一键搭建 Java 全栈开发环境。安装 Java (JDK 17/21)、Maven、Redis、ZooKeeper、Nacos、MySQL、PostgreSQL、Kafka、Elasticsearch、RocketMQ、Go、Git、Node.js、Python，配置 API Key 环境变量和中间件快捷命令。当用户要求初始化开发环境、搭建 Java 环境、配置开发机、setup dev env 时触发。
---

# Java 开发环境初始化

在新 Mac 上搭建完整的 Java 全栈开发环境，覆盖 whatsmars 项目核心技术所需的基础设施，包括 Java、Maven、中间件、数据库（MySQL、PostgreSQL）、搜索引擎、消息队列、Go、Git、Node.js、Python 环境及常用快捷命令。

## 安装策略

> **通用原则**：对于需要手动下载解压的组件（如 RocketMQ、Elasticsearch、Kafka、ZooKeeper 等），安装前先检查 `$HOME/` 和 `$HOME/Downloads/` 下是否已存在对应软件目录，若已存在则跳过安装，避免重复下载大文件或覆盖已有配置。
>
> **只安装不启动**：RocketMQ、Kafka、Elasticsearch 仅执行安装，**不要启动服务**，由用户按需自行启动。

## 执行流程

1. 安装 Homebrew（如未安装）
2. 按顺序安装各组件
3. 配置环境变量与快捷命令
4. 验证所有组件安装成功

## 安装清单

| 组件 | 安装方式 | 版本要求                      |
|---|---|---------------------------|
| Homebrew | 官方脚本 | 最新                        |
| Java (JDK 17) | `brew install openjdk@17` | 17                        |
| Java (JDK 21) | `brew install openjdk@21` | 21（JAVA_HOME 默认版本）        |
| Maven | `brew install maven` | 最新稳定版                     |
| Redis | `brew install redis` | 最新稳定版                     |
| ZooKeeper | `brew install zookeeper` | 最新稳定版                     |
| MySQL | `brew install mysql` | 最新稳定版                     |
| Nacos | 官方安装脚本 | 3.2.2                     |
| Kafka | `brew install kafka` | 最新稳定版                     |
| Elasticsearch | `brew install elasticsearch` | 最新稳定版                     |
| RocketMQ | 官方压缩包解压 | 5.5.0                     |
| Go | `brew install go` | 最新稳定版                     |
| Git | `brew install git` | 最新稳定版                     |
| Node.js/npm | `brew install node` | 最新 LTS 版本                 |
| Python | `brew install python` | 最新稳定版                     |
| PostgreSQL & pgvector | `brew install postgresql pgvector` | PostgreSQL 18，pgvector 最新 |

## 安装前检查

安装前检查各组件是否已存在，避免重复安装。根据组件特性采用不同检查方式：

| 组件 | 检查方式 | 检查命令 |
|---|---|---|
| Homebrew | 命令是否存在 | `command -v brew` |
| Java | 命令是否存在 | `java -version` |
| Maven | 命令是否存在 | `command -v mvn` |
| Redis | 端口 6379 是否占用 | `lsof -i :6379` |
| ZooKeeper | 端口 2181 是否占用；$HOME 和 $HOME/Downloads 下是否存在 zookeeper 目录 | `lsof -i :2181`；`find $HOME $HOME/Downloads -maxdepth 1 -type d -name "zookeeper*"` |
| MySQL | mysql 命令是否存在；/usr/local/mysql 目录是否存在；mysqld 进程是否运行 | `mysql --version`；`[ -d /usr/local/mysql ]`；`ps aux \| grep mysqld \| grep -v grep` |
| Nacos | nacos 快捷命令是否存在；端口 8848 是否占用；$HOME 和 $HOME/Downloads 下是否存在 nacos 目录 | `command -v nacos`；`lsof -i :8848`；`find $HOME $HOME/Downloads -maxdepth 1 -type d -name "nacos*"` |
| Kafka | 端口 9092 是否占用；$HOME 和 $HOME/Downloads 下是否存在 kafka 目录 | `lsof -i :9092`；`find $HOME $HOME/Downloads -maxdepth 1 -type d -name "kafka*"` |
| Elasticsearch | 端口 9200 是否响应；$HOME 和 $HOME/Downloads 下是否存在 elasticsearch 目录 | `curl -s localhost:9200`；`find $HOME $HOME/Downloads -maxdepth 1 -type d -name "elasticsearch*"` |
| RocketMQ | $HOME 和 $HOME/Downloads 下是否存在 rocketmq 目录 | `find $HOME $HOME/Downloads -maxdepth 1 -type d -name "rocketmq*"` |
| Go | 命令是否存在 | `command -v go` |
| Git | 命令是否存在 | `command -v git` |
| Node.js/npm | 命令是否存在 | `command -v node` |
| Python | 命令是否存在 | `command -v python3` |
| PostgreSQL | 命令是否存在 | `psql --version` |

> **执行原则**：每个组件安装前先执行对应检查，已存在则跳过，不存在则安装。

## 详细安装步骤

### 1. Homebrew

```bash
if command -v brew &>/dev/null; then
  echo "Homebrew 已安装，跳过"
else
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
fi
```

安装后将 Homebrew 加入 PATH：

```bash
eval "$(/opt/homebrew/bin/brew shellenv)"
```

建议将上面这行也加入 `~/.zshrc` 中持久生效。

### 2. Java & Maven

```bash
# 检查 Java
if java -version 2>&1 | grep -q "version"; then
  echo "Java 已安装，跳过 JDK 安装"
else
  brew install openjdk@17
  brew install openjdk@21
  sudo ln -sfn $(brew --prefix)/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
  sudo ln -sfn $(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
fi

# 检查 Maven
if command -v mvn &>/dev/null; then
  echo "Maven 已安装，跳过"
else
  brew install maven
fi
```

### 3. Redis

```bash
if lsof -i :6379 &>/dev/null; then
  echo "Redis 已运行（端口 6379），跳过"
else
  brew install redis
  brew services start redis
fi
```

### 4. ZooKeeper

```bash
ZK_DIR=$(find $HOME $HOME/Downloads -maxdepth 1 -type d -name "zookeeper*" 2>/dev/null | head -1)
if lsof -i :2181 &>/dev/null || [ -n "$ZK_DIR" ]; then
  echo "ZooKeeper 已安装，跳过"
else
  brew install zookeeper
  brew services start zookeeper
fi
```

### 5. MySQL

```bash
if mysql --version &>/dev/null || [ -d /usr/local/mysql ] || ps aux | grep mysqld | grep -v grep &>/dev/null; then
  echo "MySQL 已安装，跳过"
else
  brew install mysql
  brew services start mysql
  # 可选：设置 root 密码
  mysql_secure_installation
fi
```

### 6. Nacos

```bash
NACOS_DIR=$(find $HOME $HOME/Downloads -maxdepth 1 -type d -name "nacos*" 2>/dev/null | head -1)
if command -v nacos &>/dev/null || lsof -i :8848 &>/dev/null || [ -d "$HOME/ai-infra/nacos/standalone/nacos-3.2.2" ] || [ -n "$NACOS_DIR" ]; then
  echo "Nacos 已安装，跳过"
else
  # 下载官方安装脚本
  curl -fsSL https://nacos.io/nacos-installer.sh -o nacos-installer.sh
  # 执行安装
  bash nacos-installer.sh
fi
```

### 7. Kafka

```bash
KAFKA_DIR=$(find $HOME $HOME/Downloads -maxdepth 1 -type d -name "kafka*" 2>/dev/null | head -1)
if lsof -i :9092 &>/dev/null || [ -n "$KAFKA_DIR" ]; then
  echo "Kafka 已安装，跳过"
else
  brew install kafka
  # 仅安装，不启动服务，由用户按需自行启动
fi
# Kafka 默认依赖本机 ZooKeeper，端口 9092
```

### 8. Elasticsearch

```bash
ES_DIR=$(find $HOME $HOME/Downloads -maxdepth 1 -type d -name "elasticsearch*" 2>/dev/null | head -1)
if curl -s localhost:9200 &>/dev/null || [ -n "$ES_DIR" ]; then
  echo "Elasticsearch 已安装，跳过"
else
  brew install elasticsearch
  # 仅安装，不启动服务，由用户按需自行启动
fi
```

### 9. RocketMQ

```bash
# 在 $HOME 和 $HOME/Downloads 下搜索 rocketmq 目录
ROCKETMQ_DIR=$(find $HOME $HOME/Downloads -maxdepth 1 -type d -name "rocketmq*" 2>/dev/null | head -1)
if [ -n "$ROCKETMQ_DIR" ]; then
  echo "RocketMQ 已存在：$ROCKETMQ_DIR，跳过"
else
  cd $HOME
  curl -LO https://dlcdn.apache.org/rocketmq/5.5.0/rocketmq-all-5.5.0-bin-release.zip
  unzip rocketmq-all-5.5.0-bin-release.zip
  rm rocketmq-all-5.5.0-bin-release.zip
  echo "RocketMQ 已安装到：$HOME/rocketmq-all-5.5.0-bin-release"
fi
# 仅安装，不启动服务，由用户按需自行启动
```

### 10. Go

```bash
if command -v go &>/dev/null; then
  echo "Go 已安装，跳过"
else
  brew install go
fi
# 配置 Go 模块代理（幂等操作，始终执行）
go env -w GO111MODULE=on
go env -w GOPROXY=https://goproxy.cn,direct
# 安装 protoc（Protocol Buffers 编译器）
if command -v protoc &>/dev/null; then
  echo "protoc 已安装，跳过"
else
  brew install protobuf
fi
# 安装 protoc Go 插件（生成 Go 代码）
go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
# 确保 $(go env GOPATH)/bin 在 PATH 中
export PATH="$(go env GOPATH)/bin:$PATH"
```

### 11. Git

```bash
if command -v git &>/dev/null; then
  echo "Git 已安装，跳过"
else
  brew install git
fi
```

### 12. Node.js & npm

```bash
if command -v node &>/dev/null; then
  echo "Node.js 已安装，跳过"
else
  brew install node
fi
```

### 13. Python

```bash
if command -v python3 &>/dev/null; then
  echo "Python 已安装，跳过"
else
  brew install python
fi
```

### 14. PostgreSQL & pgvector

> **注意**：只需通过 brew 安装 pgvector 即可，**不需要**为用户在任何数据库中执行 `CREATE EXTENSION` 启用扩展。

```bash
# 检查 PostgreSQL
if psql --version &>/dev/null; then
  echo "PostgreSQL 已安装，跳过"
else
  # 一条命令同时安装 PostgreSQL 和 pgvector
  brew install postgresql pgvector
  brew services start postgresql@18
fi
```

## 环境变量配置

将以下内容追加到 `~/.zshrc`（敏感值用占位符，用户自行替换）：

```bash
# ========== Java ==========
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
# 切换 JDK 版本命令（按需使用）
# export JAVA_HOME=$(/usr/libexec/java_home -v 17)  # 切换到 JDK 17
# export JAVA_HOME=$(/usr/libexec/java_home -v 21)  # 切换到 JDK 21

# ========== Go ==========
export PATH="$(go env GOPATH)/bin:$PATH"

# ========== AI API Keys ==========
export DASHSCOPE_API_KEY="your-dashscope-api-key"
export DEEPSEEK_API_KEY="your-deepseek-api-key"
export OPENAI_API_KEY="your-openai-api-key"

# ========== Nacos ==========
export SPRING_CLOUD_NACOS_USERNAME="nacos"
export SPRING_CLOUD_NACOS_PASSWORD="your-nacos-password"
```

> **提示**：安装完成后提醒用户将占位符替换为真实值。

## Nacos 快捷命令

将以下内容追加到 `~/.zshrc`：

```bash
# Nacos 快捷命令（自动检测实际安装目录）
export NACOS_HOME=$(find $HOME/ai-infra $HOME $HOME/Downloads -maxdepth 3 -type d -name "nacos*" 2>/dev/null | while read d; do [ -f "$d/bin/startup.sh" ] && echo "$d" && break; done)

nacos() {
  case "$1" in
    start)
      echo "Starting Nacos (standalone)..."
      bash "$NACOS_HOME/bin/startup.sh" -m standalone
      ;;
    stop)
      echo "Stopping Nacos..."
      bash "$NACOS_HOME/bin/shutdown.sh"
      ;;
    status)
      if pgrep -f "nacos.home" > /dev/null; then
        echo "Nacos is running (pid: $(pgrep -f "nacos.home" | head -1))"
      else
        echo "Nacos is not running"
      fi
      ;;
    log)
      tail -f "$NACOS_HOME/logs/start.out"
      ;;
    *)
      echo "Usage: nacos {start|stop|status|log}"
      ;;
  esac
}
```

配置完成后执行 `source ~/.zshrc` 使其生效。

## 验证清单

安装完成后逐项验证：

```bash
java -version          # 应输出 openjdk 21（JAVA_HOME 默认版本）
/usr/libexec/java_home -V  # 应列出 JDK 17 和 21 两个版本
mvn -version           # 应输出 Maven 版本
redis-cli ping         # 应返回 PONG
zkCli.sh -version      # 应输出 ZooKeeper 版本
mysql --version        # 应输出 MySQL 版本
kafka-topics --version # 应输出 Kafka 版本
curl -s localhost:9200    # 如已启动应返回 ES 集群信息；未启动则跳过
go version             # 应输出 go 版本
protoc --version       # 应输出 protoc 版本
git --version          # 应输出 git 版本
node --version         # 应输出 Node.js 版本
npm --version          # 应输出 npm 版本
python3 --version      # 应输出 Python 版本
nacos status           # 应输出 Nacos 状态（需先 nacos start）
psql --version         # 应输出 PostgreSQL 版本
```

## 汇总安装结果

> **执行原则**：所有安装步骤完成后，必须执行一次汇总验证，将所有组件的安装状态、版本信息以表格形式输出给用户。表格应包含：组件名称、安装状态（已安装/未安装/运行中）、版本号或关键信息。对于未安装或异常的组件，给出修复建议。

## 常用软件安装

以下软件需要用户手动下载安装：

| 软件 | 用途 | 下载地址                                      |
|---|---|-------------------------------------------|
| Chrome | 浏览器 | https://www.google.com/chrome/            |
| IntelliJ IDEA | Java IDE | https://www.jetbrains.com/idea/download/  |
| Sublime Text | 文本编辑器 | https://www.sublimetext.com/download      |
| Apifox | API 调试工具 | https://apifox.com/                       |
| Qoder CN | AI 编程助手 | https://qoder.com.cn/                     |
| Qoder CN IDEA 插件 | IDEA 集成 Qoder | IDEA 内 Settings → Plugins 搜索 "Qoder CN" 安装 |

> **提示**：安装 IDEA 后，建议先安装 Qoder CN IDEA 插件，提升开发效率。

## 注意事项

- Homebrew 路径为 `/opt/homebrew`（Apple Silicon）
- Nacos 单机模式启动即可满足开发需求，无需集群部署
- MySQL 默认 root 无密码，建议开发环境也设置密码
- 所有 `brew services start` 的服务会开机自启，不需要可用 `brew services stop <name>` 关闭

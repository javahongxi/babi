# Babi Agent 🌏

面向开发者的 AI Coding Agent，基于 ReAct 模式提供代码分析、构建、调试等开发辅助能力。未来将持续扩展更多 Agent 功能，以内置多 Agent 协作模式融合各类开发者场景，产品核心始终聚焦 Coding Agent。

> 技术栈：AgentScope Java 2.0.0 + Spring Boot 4.1.0

## 环境准备

```bash
# 必需 — 通义千问 API Key
export DASHSCOPE_API_KEY=your_api_key

# 可选 — GitHub API 令牌（用于 GitHub 相关功能，如查询 pinned 仓库等）
export GITHUB_TOKEN=your_github_token
```

## Coding Agent

Babi 的核心产品模块，采用 ReAct（Reasoning and Acting）模式，能够读取文件、执行 Shell 命令、访问 GitHub API、搜索网页等，辅助完成代码分析、构建、调试等开发任务。

### 内置工具

| 工具                    | 说明                                    |
|-----------------------|---------------------------------------|
| `read_file`           | 读取本地文件内容                              |
| `shell_command`       | 执行 Shell 命令（ls、git、mvn 等）             |
| `fetch_url`           | 抓取网页内容，保留结构化文本                        |
| `web_search`          | 联网搜索（需 `TAVILY_API_KEY`）              |
| `http_request`        | 通用 HTTP 请求（GET/POST/PUT/DELETE/PATCH） |
| `github_api_request`  | GitHub REST API 调用，Token 自动注入         |
| `github_pinned_repos` | GitHub 置顶仓库查询（GraphQL），返回 Markdown 格式 |

**GitHub 集成特性：**
- Token 自动注入：从 `GITHUB_TOKEN` / `GH_TOKEN` 环境变量读取，Agent 不接触原始令牌
- GraphQL 查询自动修复：拦截所有 POST `/graphql` 请求，自动将单行查询格式化为 GitHub 要求的多行格式
- Pinned 仓库 Markdown 展示：自动将 GraphQL 响应解析为可读的 Markdown（仓库名、描述、Stars、Forks、语言）

### 快速开始

**命令行交互模式：**

```bash
mvn exec:java -pl babi-agent
```

启动后进入交互式对话：

```
============================================================
Babi Agent - Powered by AgentScope Java
============================================================
An AI coding assistant with file reading and shell tools.
Type 'exit' to quit.

You: 帮我看看当前目录有哪些文件
BabiAgent: 我来执行 ls 命令查看当前目录的文件...
```

**Spring Boot Web 服务模式：**

```bash
mvn spring-boot:run -pl babi-agent
```

**打开浏览器访问 `http://localhost:8900` 即可使用聊天界面。**

聊天界面支持 Markdown 渲染（标题、代码块、表格、链接等），实时显示工具调用状态，支持多轮会话（通过 Session ID 保持上下文）。

### Web API

| 接口                 | 方法  | 说明                      |
|--------------------|-----|-------------------------|
| `/api/chat/stream` | GET | SSE 流式输出，实时推送文本和工具调用事件  |
| `/api/chat/send`   | GET | 同步接口，等待 Agent 完整回复后一次返回 |

**参数：**

| 参数          | 必填 | 默认值       | 说明                |
|-------------|----|-----------|-------------------|
| `message`   | 是  | -         | 发送给 Agent 的消息     |
| `sessionId` | 否  | `default` | 会话 ID，用于多轮对话上下文保持 |

### curl 示例

**流式输出（SSE）：**

```bash
# 基本用法
curl -N -G "http://localhost:8900/api/chat/stream" \
  --data-urlencode "message=帮我执行命令pwd"

# 指定 sessionId
curl -N -G "http://localhost:8900/api/chat/stream" \
  --data-urlencode "message=查看当前 git 状态" \
  --data-urlencode "sessionId=dev-001"

# 读取文件
curl -N -G "http://localhost:8900/api/chat/stream" \
  --data-urlencode "message=帮我读一下 pom.xml 的内容"

# 查询 GitHub 置顶仓库
curl -N -G "http://localhost:8900/api/chat/stream" \
  --data-urlencode "message=查看我的 pinned 仓库"

# 查询 GitHub 仓库 Issues
curl -N -G "http://localhost:8900/api/chat/stream" \
  --data-urlencode "message=查看 javahongxi/whatsmars 的 issues"
```

> **注意：** 中文消息需要使用 `--data-urlencode` 让 curl 自动进行 URL 编码，直接拼在 URL 里会导致 400 错误。`-N` 参数禁用缓冲，确保实时看到流式输出。

### SSE 事件格式

流式接口推送以下事件：

**文本增量（token）：**
```
event: token
data: {"type":"token","data":"你好"}
```

**工具调用开始（tool_call）：**
```
event: tool_call
data: {"type":"tool_call","tool":"shell_command"}
```

**工具调用结果（tool_result）：**
```
event: tool_result
data: {"type":"tool_result","tool":"shell_command","state":"SUCCESS"}
```

**完成标记（done）：**
```
event: done
data: {"type":"done"}
```

&copy; [hongxi.org](http://hongxi.org)
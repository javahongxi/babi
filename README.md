# Babi Agent ♻️

面向开发者的 AI Coding Agent，基于 ReAct 模式提供代码分析、构建、调试等开发辅助能力。未来将持续扩展更多 Agent 功能，以内置多 Agent 协作模式融合各类开发者场景，产品核心始终聚焦 Coding Agent。

> 技术栈：AgentScope Java 2.0.0 + Spring Boot 4.1.0

## 环境准备

```bash
# 阿里云百炼 API Key
export DASHSCOPE_API_KEY=your_api_key

# 可选 — GitHub API 令牌（用于 GitHub 相关功能，如查询 pinned 仓库等）
export GITHUB_TOKEN=your_github_token
```

## 快速开始

### 命令行交互模式

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

### Web 服务模式

```bash
mvn spring-boot:run -pl babi-agent
```

打开浏览器访问 `http://localhost:8900` 即可使用聊天界面。

聊天界面支持 Markdown 渲染（标题、代码块、表格、链接等），实时显示工具调用状态，支持多轮会话（通过 Session ID 保持上下文）。

## 说明

### 关于 curl

```bash
# 基本用法
curl -N -G "http://localhost:8900/api/chat/stream" \
  --data-urlencode "message=帮我执行命令pwd"
```

中文消息需要使用 `--data-urlencode` 让 curl 自动进行 URL 编码，直接拼在 URL 里会导致 400 错误。`-N` 参数禁用缓冲，确保实时看到流式输出。

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
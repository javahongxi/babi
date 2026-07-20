# Babi Agent ♻️

面向开发者的 AI Coding Agent，基于 ReAct 模式提供代码分析、构建、调试等开发辅助能力。

> 技术栈：AgentScope Java 2.0.0 + Spring Boot 4.1.0

## 特性

- **代码读写** — 读取、分析源码，精准文本替换编辑文件
- **代码搜索** — 基于 ripgrep/grep 的模式匹配，快速定位代码
- **Shell 执行** — 运行构建、测试、部署等终端命令
- **网页抓取** — 获取网页内容，保留结构化文本
- **Web 搜索** — 模型内置联网检索，实时获取最新信息（无需额外 API Key）
- **HTTP 请求** — 调用任意 REST API
- **GitHub 集成** — 通过 API 操作 Issues、PR、仓库、Pinned Repos 等
- **Skills 扩展** — Markdown 定义的可复用工作流指令，支持全局与项目级加载
- **任务追踪** — 内置 Todo 列表，可视化多步骤任务进度
- **双端交互** — Web 聊天界面（Markdown 渲染 + 工具状态可视化）与 CLI 两种模式

## 环境准备

```bash
# 阿里云百炼 API Key
export DASHSCOPE_API_KEY=your_api_key

# 可选 — GitHub API 令牌（用于 GitHub 相关功能，如查询 pinned 仓库等）
export GITHUB_TOKEN=your_github_token
```

## 快速开始

### Web 聊天界面（推荐）

```bash
mvn spring-boot:run -pl babi-agent
```

打开浏览器访问 `http://localhost:8900`，即可在聊天界面中与 Babi Agent 交互。

支持 Markdown 实时渲染、工具调用状态可视化、多轮会话（Session ID 隔离上下文）。

### 命令行模式

```bash
mvn exec:java -pl babi-agent
```

启动后进入交互式终端对话，适合快速调试或无 GUI 环境。

&copy; [hongxi.org](http://hongxi.org)
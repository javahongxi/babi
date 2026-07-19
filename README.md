# Babi Agent ♻️

面向开发者的 AI Coding Agent，基于 ReAct 模式提供代码分析、构建、调试等开发辅助能力。
未来将持续扩展更多 Agent 功能，以内置多 Agent 协作模式融合各类开发者场景，产品核心始终聚焦 Coding Agent。

> 技术栈：AgentScope Java 2.0.0 + Spring Boot 4.1.0

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
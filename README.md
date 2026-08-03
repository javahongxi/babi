# Babi Agent ♻️

面向开发者的 AI Coding Agent，基于 ReAct 模式提供代码分析、构建、调试等开发辅助能力。

本项目包含三个 Java 实现版本：

| 模块                 | 技术栈                                    | 定位                        |
|----------------------|-------------------------------------------|-----------------------------|
| **babi-agent**       | AgentScope Java 2.0.0 + Spring Boot 4.1.0 | 主版本，功能完整            |
| **babi-graph**       | LangGraph4j 1.8.20 + Spring Boot 4.1.0    | 替代实现，基于图编排        |
| **babi-spring**      | Spring AI 2.0.0 + Spring Boot 4.1.0       | 轻量实现，纯 Spring AI 驱动 |

另有 Python 实现版本：

| 项目                           | 技术栈                | 定位                       |
|--------------------------------|-----------------------|----------------------------|
| **javahongxi/babi-langgraph**  | LangGraph + LangChain | 主力打磨版本，紧跟 AI 生态 |
| **javahongxi/babi-agentscope** | AgentScope Python     | 与 Java 版互为跨语言参照   |

> 各版本共享相似的工具能力与交互体验，可根据技术偏好选择使用。

## 特性

- **代码读写** — 读取、分析源码，精准文本替换编辑文件
- **代码搜索** — 基于 ripgrep/grep 的模式匹配，快速定位代码
- **Shell 执行** — 运行构建、测试、部署等终端命令
- **网页抓取** — 获取网页内容，保留结构化文本
- **Web 搜索** — 模型内置联网检索，实时获取最新信息（无需额外 API Key）
- **HTTP 请求** — 调用任意 REST API
- **GitHub 集成** — 通过 API 操作 Issues、PR、仓库、Pinned Repos 等
- **Skills 扩展** — Markdown 定义的可复用工作流指令，支持全局、Babi 专属、项目级三级加载
- **任务追踪** — 内置 Todo 列表，可视化多步骤任务进度
- **双端交互** — Web 聊天界面（Markdown 渲染 + 工具状态可视化）与 CLI 两种模式
- **会话持久化** — 基于文件的会话存储，跨重启保持对话上下文

## 环境准备

各模块使用不同的模型与 SDK：

| 模块            | 模型              | SDK                                   | 环境变量            |
|-----------------|-------------------|---------------------------------------|---------------------|
| **babi-agent**  | Qwen-Plus         | agentscope-extensions-model-dashscope | `DASHSCOPE_API_KEY` |
| **babi-graph**  | Qwen-Plus         | langchain4j-open-ai                   | `DASHSCOPE_API_KEY` |
| **babi-spring** | DeepSeek-V4-Flash | spring-ai-starter-model-deepseek      | `DEEPSEEK_API_KEY`  |

> babi-graph 支持在 Qwen / DeepSeek 之间自由切换，切换至 DeepSeek 时使用 `DEEPSEEK_API_KEY`。

```bash
# 阿里云百炼 API Key（babi-agent、babi-graph 使用）
export DASHSCOPE_API_KEY=your_api_key

# DeepSeek API Key（babi-spring 使用）
export DEEPSEEK_API_KEY=your_api_key

# 可选 — GitHub API 令牌（用于 GitHub 相关功能，如查询 pinned 仓库等）
export GITHUB_TOKEN=your_github_token
```

## 快速开始

### Web 聊天界面（推荐）

```bash
# AgentScope 版本（主版本）
mvn spring-boot:run -pl babi-agent

# LangGraph4j 版本
mvn spring-boot:run -pl babi-graph

# Spring AI 版本
mvn spring-boot:run -pl babi-spring
```

打开浏览器访问 `http://localhost:8900`（AgentScope）、`http://localhost:8901`（LangGraph4j）或 `http://localhost:8902`（Spring AI），即可在聊天界面中与 Babi Agent 交互。

支持 Markdown 实时渲染、工具调用状态可视化、多轮会话（Session ID 隔离上下文）。

> **流式行为差异**：babi-agent 和 babi-graph 均为全程真流式输出（工具调用期间也能逐 token 推送）；babi-spring 在工具执行期间前端短暂等待，最终回答仍为流式。

### 命令行模式

```bash
# 一键安装（构建 + 配置环境变量，之后终端直接输入 babi 启动）
./install.sh

# 安装后使用（默认以当前目录为工作区）
babi                            # 启动 Babi Agent
babi --workspace ~/other-project # 指定其他工作区（可选）
```

> 开发调试时也可直接运行：`mvn exec:java -pl babi-agent`

## Skills 扩展

Skills 是 Markdown 格式的可复用工作流指令，从以下目录自动加载（后者覆盖前者）：

| 优先级 | 目录 | 说明 |
|--------|------|------|
| 低 | `~/.agents/skills/` | 全局共享 Skills |
| 中 | `~/.babi/skills/` | Babi 专属 Skills |
| 高 | `{workspace}/.qoder/skills/` | 项目级 Skills（相对于工作区根目录） |

支持两种文件格式：
- **单文件**：`my-skill.md`
- **目录格式**：`my-skill/SKILL.md`

## 卸载

```bash
./uninstall.sh
```

&copy; [hongxi.org](http://hongxi.org)
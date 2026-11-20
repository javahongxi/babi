# Babi Agent ♻️

本地优先的 AI 办公助手，基于 AgentScope 构建。Agent 引擎、工具执行与会话存储全部运行在用户本机，模型 API 直连（BYOK），无需远端后端——文件与对话数据不离开本机。编码辅助是核心能力之一。

| 模块           | 技术栈                    | 说明                       |
|----------------|---------------------------|----------------------------|
| **babi-agent** | AgentScope Java 2.0.4     | 后端主干，Web + CLI 双模式 |
| **babi-ui**    | Vue 3 + TypeScript + Vite | 唯一前端，可定制聊天界面   |

> 历史上的 LangGraph4j / Spring AI 对照实现已归档至 `archive/multi-framework` 分支（tag: `v0.9-multi-framework`）。

## 特性

- **本地优先** — 无远端后端，会话与文件全部留在本机，自带 API Key 直连模型服务
- **代码读写** — 读取、分析源码，精准文本替换编辑文件
- **代码搜索** — 基于 ripgrep/grep 的模式匹配，快速定位代码
- **文件搜索** — 基于 glob 模式的文件名匹配，快速发现目标文件（如 `**/*.java`）
- **Shell 执行** — 运行构建、测试、部署等终端命令
- **网页抓取** — 获取网页内容，保留结构化文本
- **Web 搜索** — 模型内置联网检索，实时获取最新信息（无需额外 API Key）
- **HTTP 请求** — 调用任意 REST API
- **GitHub 集成** — 通过 API 操作 Issues、PR、仓库、Pinned Repos 等
- **任务追踪** — 通过 todo_write 工具管理待办事项，追踪多步骤任务执行进度
- **Skills 扩展** — Markdown 定义的可复用工作流指令，支持全局、Babi 专属、项目级三级加载
- **事件实时推送** — 总线按会话隔离接管工具调用链，调用的工具名和成败状态实时推送前端
- **双端交互** — Web 聊天界面（Markdown 渲染 + 工具状态可视化）与 CLI 两种模式
- **会话持久化** — 基于文件的会话存储，跨重启保持对话上下文
- **会话中断** — 用户可随时终止 LLM 生成，真正停止 token 输出以节省资源消耗
- **思考过程展示** — 实时展示 LLM 推理过程（reasoning），以可折叠区块可视化呈现
- **图片生成** — 优先使用 Skills 扩展，找不到技能时使用内置的 generate_image 工具
- **模型选择** — 前端下拉框动态切换模型，支持 Qwen、DeepSeek、GLM 等系列模型

## 环境准备

默认使用 Qwen 系列模型：

| 模块           | 模型          | SDK                                   | 环境变量            |
|----------------|---------------|---------------------------------------|---------------------|
| **babi-agent** | qwen3.8-flash | agentscope-extensions-model-dashscope | `DASHSCOPE_API_KEY` |

> babi-agent 支持配置备用模型，当主模型不可用时，自动切换到备用模型，这是由框架支持的。

```bash
# 阿里云百炼 API Key
export DASHSCOPE_API_KEY=your_api_key

# 可选 — GitHub API 令牌（用于 GitHub 相关功能，如查询 pinned 仓库等）
export GITHUB_TOKEN=your_github_token
```

## 快速开始

### Web 聊天界面（推荐）

```bash
# 1. 启动后端 http://localhost:8900
mvn spring-boot:run -pl babi-agent

# 2. 启动前端 http://localhost:5173
cd babi-ui
npm install
npm run dev
```

支持 Markdown 实时渲染、工具调用状态可视化、多轮会话（Session ID 隔离上下文）。

### babi-ui（唯一前端）

babi-ui 用 Vue 3 + TypeScript 实现聊天界面，是 Babi Agent 的唯一前端，后端不再内置静态页面。

```bash
cd babi-ui
npm run dev       # 开发预览 http://localhost:5173；/api 代理到本地 8900（需先启动 babi-agent）
npm run build     # 产出到 babi-ui/dist/，可部署到任意静态服务器
```

代理目标端口写在 `vite.config.ts`，默认指向 8900。

前端特性：

- **热模块替换** — Vite 监听源码，改文件即时推送到浏览器，无需重启后端或手动刷新
- **组件化 + 类型化** — 界面拆成独立组件，`types/chat.ts` 以 TypeScript 建模后端契约，字段不符编译期即报错
- **按需加载** — highlight.js 仅注册常用语言，产物体积可控

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

| 优先级 | 目录                         | 说明                                |
|--------|------------------------------|-------------------------------------|
| 低     | `~/.agents/skills/`          | 全局共享 Skills                     |
| 中     | `~/.babi/skills/`            | Babi 专属 Skills                    |
| 高     | `{workspace}/.qoder/skills/` | 项目级 Skills（相对于工作区根目录） |

支持两种文件格式：
- **单文件**：`my-skill.md`
- **目录格式**：`my-skill/SKILL.md`

## 卸载

```bash
./uninstall.sh
```

&copy; [hongxi.org](http://hongxi.org)
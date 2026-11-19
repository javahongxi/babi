# 子 Agent 声明文件机制解析

解析 `{workspace}/subagents/*.md` 声明文件从"落盘"到"被框架消费"的完整链路。演示步骤见 [subagent-demo.md](subagent-demo.md)。

核心结论：这些 `.md` 是**声明式子 agent 配置**——babi 只负责首次落盘，AgentScope `HarnessAgent` 内置的 `DynamicSubagentsMiddleware` 在每轮推理前动态加载它们，把子 agent 目录喂给主 LLM，并在 LLM 决定派活时按声明自动孵化出带专属提示词和工具集的子 agent。

## 1. babi 侧：首次启动落盘（种子）

入口在 `AgentConfiguration.workspacePath()` Bean：

```java
AgentUtils.initAgentsMd(path);
AgentUtils.initSubagents(path);   // 种子 4 个内置声明
```

`AgentUtils.initSubagents` 的逻辑：

1. 目标目录 `{workspace}/subagents/`
2. 遍历内置声明名单 `SUBAGENT_SPECS`：`code-reviewer.md`、`test-runner.md`、`researcher.md`、`doc-writer.md`
3. **文件已存在则跳过**——用户的修改甚至删除都不会被覆盖（幂等、非破坏性）
4. 不存在则从 classpath 资源 `/workspace/subagents/`（打包进 jar 的 `babi-agent/src/main/resources/workspace/subagents/`）复制到工作区
5. IO 失败只打 warn 日志，不中断启动

与 `initAgentsMd`（初始化 `AGENTS.md`）是同一套"首次运行落盘默认配置"机制。

## 2. 文件格式：元数据 + 提示词

由框架的 `AgentSpecLoader` 解析，以 `code-reviewer.md` 为例：

```markdown
---
description: Reviews code for correctness, security, and performance issues. ...
workspace:
  mode: shared
maxIters: 12
---
You are a senior code reviewer working inside the project workspace.
...
```

| 部分 | 含义 |
|------|------|
| 文件名 | 子 agent 的名字 / agent_id（如 `code-reviewer`），front matter 中不允许再写 `name` |
| `description` | **必填**。给主 agent 的 LLM 看的"能力简介"，决定它何时派活给这个子 agent |
| `workspace.mode` | `shared`=直接操作主工作区；`isolated`=独立沙箱目录 |
| `workspace.path` | 可选。指定后正文必须为空，子 agent 的系统提示词改从 `<path>/AGENTS.md` 运行时读取 |
| `maxIters` | 可选（默认 10），子 agent 自己的最大推理轮数 |
| `tools` | 可选，工具白名单 |
| Markdown 正文 | 子 agent 的 **system prompt** |

## 3. 框架侧：每轮推理动态扫描

babi 构建 `HarnessAgent` 时未禁用 subagents，框架默认挂载 `DynamicSubagentsMiddleware`（只挂 `ON_REASONING` 扩展点）。每轮 LLM 推理前执行 `reloadEntries`：

1. **Layer 2（base）**：`AgentSpecLoader.loadFromDirectory` 直接扫描本地 `{workspace}/subagents/*.md`
2. **Layer 1（override）**：经 `AbstractFilesystem` 的 `glob("*.md", "subagents")` + 逐文件 `read` 再加载一遍（支持按用户隔离的远端存储场景）
3. **合并**：同名覆盖，优先级 Layer 1 > Layer 2 > 代码注册（builder 静态注册）
4. 为每个声明构建 `SubagentFactory`（延迟工厂，真正 spawn 时才实例化子 agent），失败的声明打 warn 后跳过

这就是 `initSubagents` 只在文件不存在时才复制的原因：**手改 `.md` 下一轮对话立即生效，无需重启**。

## 4. 注入主 agent 上下文 + 自动注册工具

`onReasoning` 中：

- 把合并后的子 agent 目录（名字 + description）渲染成说明段，**prepend 到主 agent 的 system message**，让 LLM 知道"手下有哪些专家可以调度"（`hidden=true` 的声明不展示）
- 附带任务摘要状态消息（`buildSubagentStateMsg`），让 LLM 感知在途/已完成的子 agent 任务
- 框架自动向 toolkit 注册编排工具：`agent_spawn`（AgentSpawnTool）与 `task`（TaskTool，含 task_output / task_cancel / task_list）

## 5. 运行时派活

用户说"帮我 review 一下这个模块"时，主 agent 的 LLM 调用：

```
agent_spawn(agent_id="code-reviewer", task="review xxx 模块...")
```

框架用该声明的工厂创建一个**子 HarnessAgent**：

- 正文作为 sysPrompt
- 按 `tools` 白名单裁剪工具
- 按 `maxIters` 限制轮数
- 按 `workspace.mode` 决定文件访问范围

任务异步执行，主 agent 之后通过 `task_output` 取回结果并转述给用户。

## 6. babi 侧：事件展示链路

`ToolNotificationMiddleware` 把子 agent 编排工具的调用识别为 **SUBAGENT 事件**（替代普通 TOOL_CALL），经 `ToolEventBus` → `BabiAgentController` 映射为 SSE `subagent` 帧（含 tool/agent/task 字段），前端 `useChat.ts` 维护 RUNNING 条目并由 tool_result 原位回填状态，`ToolEventList.vue` 渲染为紫色边条独立条目。前后端以 `SUBAGENT_TOOLS` 常量保持契约一致。

## 相关代码位置

babi 侧：

| 关注点 | 文件 |
|--------|------|
| 声明种子逻辑 | `babi-agent/.../util/AgentUtils.java` → `initSubagents` |
| 种子调用点 | `babi-agent/.../config/AgentConfiguration.java` → `workspacePath()` |
| 声明资源文件 | `babi-agent/src/main/resources/workspace/subagents/` |
| SUBAGENT 事件路由 | `babi-agent/.../middleware/ToolNotificationMiddleware.java` |
| SSE 帧映射 | `babi-agent/.../controller/BabiAgentController.java` |
| 前端事件处理 / 渲染 | `babi-ui/src/composables/useChat.ts`、`components/ToolEventList.vue` |

框架侧（agentscope-java / agentscope-harness）：

| 关注点 | 类 |
|--------|-----|
| 动态扫描中间件 | `io.agentscope.harness.agent.middleware.DynamicSubagentsMiddleware` |
| 声明解析（front matter + 正文） | `io.agentscope.harness.agent.subagent.AgentSpecLoader` |
| 目录渲染 / 状态消息 | `io.agentscope.harness.agent.middleware.SubagentsMiddleware` |
| 派生工具 | `io.agentscope.harness.agent.tool.AgentSpawnTool`、`TaskTool` |
| 子 agent 实例化 | `io.agentscope.harness.agent.subagent.SubagentFactory`、`DefaultAgentManager` |
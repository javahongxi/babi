# @path 文件附件指南

在聊天消息里用 `@路径` 直接引用文件，框架会自动读取文件内容并附加给模型——对齐 Claude Code CLI 的 `@file` 输入语法。**后端零代码**：AgentScope 2.0.4 的 `HarnessAgent` 默认挂载 `AtPathExpansionMiddleware`，babi 未禁用。

## 1. 用法

直接在输入框（Web 或 CLI）里写：

```
帮我总结 @doc/报告.md 的要点
对比 @src/a.java 和 @src/b.java 的实现差异
看看 @~/notes/todo.txt 里有什么
```

模型收到的消息末尾会自动追加文件内容块：

```
帮我总结 @doc/报告.md 的要点

<attached_file path="doc/报告.md">
（文件内容）
</attached_file>
```

## 2. 语法规则

| 规则 | 说明 |
|------|------|
| 触发条件 | `@` 后紧跟的路径 token 必须含 `/`、`.` 或 `~` 之一（避免误吞邮箱、@人名） |
| 相对路径 | 基于 workspace 解析（Web 模式为 `~/babi-workspace`） |
| 绝对路径 / `~` | babi 文件系统为 UNRESTRICTED 模式，可引用本机任意路径；`~/` 自动展开为用户主目录 |
| 尾部标点 | `.` `,` `;` `:` `!` `?` 及中文标点自动剔除，不会混进路径 |
| 去重 | 同一消息里重复引用同一路径只附加一次 |
| 行数上限 | 每个文件最多读取 1000 行，防止大文件撑爆上下文 |
| 二进制文件 | 自动跳过（base64 内容不进消息），PDF/图片等请用知识库或相应工具 |

## 3. 行为边界

- 只展开 **USER 消息**，工具结果与助手消息不受影响
- 读取失败（文件不存在、越权等）**静默跳过**：`@路径` 原文保留，模型仍可按普通路径调用 read 工具兜底
- 展开发生在 agent 入口（ON_AGENT 扩展点），早于推理与上下文截断
- 前端不做 @ 补全（极简交互设计），路径需手动输入或从文件树复制

## 4. 快速验证

```bash
mkdir -p ~/babi-workspace/demo
echo "暗号是蓝色河马" > ~/babi-workspace/demo/secret.txt
```

对话发送：`@demo/secret.txt 里的暗号是什么？`——模型应直接答出「蓝色河马」而无需调用任何工具（工具栏无 read 调用即为展开生效）。

## 相关代码位置

| 关注点 | 类 |
|--------|-----|
| @path 展开中间件 | `io.agentscope.harness.agent.middleware.AtPathExpansionMiddleware`（agentscope-harness，默认挂载） |
| 禁用开关（babi 未使用） | `HarnessAgent.Builder.disableAtPathExpansion()` |
| 文件读取 | `LocalFilesystemSpec` UNRESTRICTED 模式（见 `AgentConfiguration`） |
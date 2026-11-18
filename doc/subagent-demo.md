# 子 Agent 演示指南

在 babi-ui 页面上测试多 agent / 子 agent 能力的完整示例。

前置知识：babi 基于 AgentScope 2.0.4 的 `HarnessAgent`，子 agent 机制**默认启用**——框架自动注册 `agent_spawn`、`task`、`task_output` 等编排工具，并在每轮推理前动态扫描 `{workspace}/subagents/*.md` 声明文件，增删即时生效、无需重启。

## 1. 启动

```bash
# 终端 1：后端（端口 8900）
export DASHSCOPE_API_KEY=your_key
mvn spring-boot:run -pl babi-agent

# 终端 2：前端（http://localhost:5173，/api 已代理到 8900）
cd babi-ui && npm run dev
```

后端启动日志里确认两件事：

- `Agent workspace: /Users/hongxi/babi-workspace`（Web 模式固定用这个目录，不支持动态切换）
- `Seeded subagent declaration: code-reviewer.md` / `test-runner.md`（首次启动会自动种到 `~/babi-workspace/subagents/`，已存在则不覆盖）

## 2. 准备测试素材

自带的两个子 agent 声明均为 `shared` 工作区模式，操作对象就是 `~/babi-workspace`。往里丢一两个带点小问题的 Java 文件，审查才有靶子：

```bash
mkdir -p ~/babi-workspace/demo && cat > ~/babi-workspace/demo/Demo.java <<'EOF'
public class Demo {
    public static int divide(int a, int b) { return a / b; }  // 除零风险
    public static String find(java.util.List<String> l) { return l.get(0); }  // 越界风险
}
EOF
```

## 3. 对话触发

在页面上发送以下消息（措辞越直白，LLM 越容易真的走 `agent_spawn` 而不是自己干）：

| 演示场景   | 示例消息                                              | 走的事件链路                    |
|------------|-------------------------------------------------------|---------------------------------|
| 派生 + 同步 | `派一个 code-reviewer 子 agent 审查 demo/Demo.java`    | `agent_spawn`                   |
| 测试执行   | `用 test-runner 子 agent 检查这个目录里的代码能不能编译通过` | `agent_spawn`               |
| 后台任务   | `用后台任务的方式派 test-runner 跑一遍，完成后告诉我结果` | `agent_spawn` + `task_output` 轮询 |

## 4. 预期效果

消息气泡下方的工具栏会出现紫色边条的独立条目：

```
🤖 子 agent code-reviewer — 审查 demo/Demo.java · 执行中…
```

任务完成后同一条目原位更新为 `· 已完成`（失败则显示 `· 失败`）。主 agent 的回复正文中会转述子 agent 的审查结论。

## 5. 两个已知观察点

1. **条目只显示最近 2 条**（`ToolEventList` 的 visibleCount 限制），多派子 agent 时旧条目会被折叠进「⋯ 已调用 N 次工具」摘要行——一次派一个最直观。
2. **LLM 不是每次都愿意派**。如果它自己把活干了，追问一句「必须通过 agent_spawn 委托给子 agent」即可；也可以直接问「你现在有哪些子 agent 可用」来验证声明加载成功（答不出 code-reviewer / test-runner 就是声明解析有问题）。

## 6. 不动用 LLM 的静态验证

```bash
# 声明文件是否就位
ls ~/babi-workspace/subagents/

# 格式约定（Markdown + YAML front matter，description 必填）
cat ~/babi-workspace/subagents/code-reviewer.md
```

声明文件格式要点：

```markdown
---
description: 一句话说明能力，LLM 据此决定是否派生（必填）
workspace:
  mode: shared          # shared=直接操作主工作区；isolated=独立沙箱目录
maxIters: 12            # 可选
tools: [read_file]      # 可选，工具白名单
---
正文即子 agent 的系统提示词。
```

## 相关代码位置

| 关注点             | 文件                                                                     |
|--------------------|--------------------------------------------------------------------------|
| 声明种子逻辑       | `babi-agent/.../util/AgentUtils.java` → `initSubagents`                  |
| 声明资源文件       | `babi-agent/src/main/resources/workspace/subagents/`                     |
| SUBAGENT 事件路由  | `babi-agent/.../middleware/ToolNotificationMiddleware.java`              |
| SSE 帧映射         | `babi-agent/.../controller/BabiAgentController.java`                     |
| 前端事件处理       | `babi-ui/src/composables/useChat.ts`（`SUBAGENT_TOOLS` 契约在 `types/chat.ts`） |
| 前端条目渲染       | `babi-ui/src/components/ToolEventList.vue`                               |

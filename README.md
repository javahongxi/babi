# Babi Agents 🌏

基于 [AgentScope Java](https://github.com/agentscope-ai/agentscope-java) 构建的 AI Agent 集合项目。每个 Agent 作为独立模块，共享统一的工具体系和模型接入层。

> 技术栈：AgentScope Java 2.0.0 + Spring Boot 4.1.0

前置条件
```bash
export DASHSCOPE_API_KEY=your_api_key
```

## babi-codingagent

采用 ReAct（Reasoning and Acting）模式的编程助手，能够读取文件、执行 Shell 命令，辅助完成代码分析、构建、调试等开发任务。

### 快速开始

**命令行交互模式：**

```bash
mvn exec:java -pl babi-codingagent -Dexec.mainClass="org.hongxi.babi.codingagent.CodingAgentCli"
```

启动后进入交互式对话：

```
============================================================
Babi Coding Agent - Powered by AgentScope Java
============================================================
An AI coding assistant with file reading and shell tools.
Type 'exit' to quit.

You: 帮我看看当前目录有哪些文件
BabiCodingAgent: 我来执行 ls 命令查看当前目录的文件...
```

**Spring Boot Web 服务模式：**

```bash
mvn spring-boot:run -pl babi-codingagent
```

**打开浏览器访问 http://localhost:8082 即可使用聊天界面。**

### Web API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/chat/stream` | GET | SSE 流式输出，实时推送文本和工具调用事件 |
| `/api/chat/send` | GET | 同步接口，等待 Agent 完整回复后一次返回 |

**参数：**

| 参数 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `message` | 是 | - | 发送给 Agent 的消息 |
| `sessionId` | 否 | `default` | 会话 ID，用于多轮对话上下文保持 |

### curl 示例

**流式输出（SSE）：**

```bash
# 基本用法
curl -N -G "http://localhost:8082/api/chat/stream" \
  --data-urlencode "message=帮我执行命令pwd"

# 指定 sessionId
curl -N -G "http://localhost:8082/api/chat/stream" \
  --data-urlencode "message=查看当前 git 状态" \
  --data-urlencode "sessionId=dev-001"

# 读取文件
curl -N -G "http://localhost:8082/api/chat/stream" \
  --data-urlencode "message=帮我读一下 pom.xml 的内容"

# 构建项目
curl -N -G "http://localhost:8082/api/chat/stream" \
  --data-urlencode "message=执行 mvn compile 并告诉我结果"
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

### 自定义工具

在 `tool/` 包下新建类，使用 `@Tool` 和 `@ToolParam` 注解即可：

```java
public class MyTool {

    @Tool(name = "my_tool", description = "工具描述")
    public String doSomething(
            @ToolParam(name = "param1", description = "参数说明") String param1) {
        return "result";
    }
}
```

然后在 Controller 或 CLI 中注册：

```java
toolkit.registerTool(new MyTool());
```

### 核心 API 用法

Agent 构建使用 AgentScope Java 的 Builder 模式：

```java
ReActAgent agent = ReActAgent.builder()
        .name("BabiCodingAgent")           // Agent 名称
        .sysPrompt("You are a ...")        // 系统提示词
        .model("dashscope:qwen-plus")      // 模型（格式：provider:model_name）
        .toolkit(toolkit)                  // 工具集
        .maxIters(20)                      // 最大推理-行动迭代次数
        .build();

// 流式调用
agent.streamEvents(new UserMessage("你的问题"))
       .doOnNext(event -> {
           if (event instanceof TextBlockDeltaEvent e) {
               System.out.print(e.getDelta());
           }
       })
       .blockLast();
```

&copy; [hongxi.org](http://hongxi.org)
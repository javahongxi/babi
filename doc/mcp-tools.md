# MCP 工具接入指南

通过 `{workspace}/tools.json` 把任意 MCP（Model Context Protocol）server 的工具接入 BabiAgent。**后端零代码**：AgentScope 2.0.4 的 `HarnessAgent` 构建时默认加载该文件并自动注册 MCP 工具，MCP SDK 已随 agentscope-core 传递引入。

## 1. 配置格式

在 workspace 目录（Web 模式为 `~/babi-workspace`）下创建 `tools.json`，结构与 Claude Desktop 等 MCP 客户端的标准配置一致：

```json
{
  "mcpServers": {
    "filesystem": {
      "transport": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/Users/you/Documents"],
      "env": {}
    },
    "remote-api": {
      "transport": "http",
      "url": "https://mcp.example.com/mcp",
      "headers": { "Authorization": "Bearer ${MY_MCP_TOKEN}" },
      "enableTools": ["search", "fetch"]
    }
  }
}
```

字段说明（`McpServerConfig`）：

| 字段 | 适用 transport | 说明 |
|------|----------------|------|
| `transport` | 全部 | `stdio` / `sse` / `http`（streamable HTTP） |
| `command` / `args` / `env` | stdio | 启动本地 MCP server 进程的命令、参数、环境变量 |
| `url` / `headers` / `queryParams` | sse / http | 远端 server 地址与请求头 |
| `enableTools` | 全部 | 可选白名单，只导入列出的工具；缺省导入该 server 全部工具 |
| `timeout` / `initializationTimeout` | 全部 | 可选超时控制 |

顶层还支持全局工具过滤（`ToolFilter`，**同时作用于内置工具与自定义工具**）：

```json
{ "allow": ["read_file", "grep_files"], "deny": ["shell_execute"] }
```

- `allow`：非空时，未列出的工具被隐藏
- `deny`：列出的工具一律移除，优先级高于 allow

## 2. 密钥不落盘

解析前会对原始 JSON 做 `${ENV_VAR}` 环境变量替换（`System.getenv`），token 放环境变量即可：

```json
"headers": { "Authorization": "Bearer ${MY_MCP_TOKEN}" }
```

## 3. 关键行为与限制

- **读取时机**：`HarnessAgent` 是单例 Bean，`tools.json` 只在应用启动构建 agent 时读取一次——**修改后必须重启后端**（区别于 `subagents/*.md` 的每轮动态扫描）
- **容错**：文件缺失、为空或解析失败都不会中断启动，仅记日志并回退到默认工具集
- **事件展示**：MCP 工具调用走普通 TOOL_CALL 事件链路，前端工具栏正常显示
- babi **不会**自动种子 `tools.json`（避免误启动外部进程），需要时手动创建

## 4. 快速验证

```bash
cat > ~/babi-workspace/tools.json <<'EOF'
{
  "mcpServers": {
    "everything": {
      "transport": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-everything"]
    }
  }
}
EOF
mvn spring-boot:run -pl babi-agent
```

启动日志出现 MCP server 注册信息后，对话里问「你现在有哪些工具」应能看到 everything server 的工具（如 `echo`）。验证完删除该文件即可恢复默认。

## 相关代码位置

| 关注点 | 类（agentscope-harness） |
|--------|--------------------------|
| 配置加载（含 ${ENV_VAR} 替换） | `io.agentscope.harness.agent.tools.ToolsConfigLoader` |
| MCP 注册 | `io.agentscope.harness.agent.tools.McpServerRegistrar` |
| 配置模型 | `io.agentscope.harness.agent.tools.ToolsConfig` / `McpServerConfig` |
| 全局工具过滤 | `io.agentscope.harness.agent.tools.ToolFilter` |
| 底层 MCP 客户端 | `io.agentscope.core.tool.mcp.McpClientBuilder`（agentscope-core） |
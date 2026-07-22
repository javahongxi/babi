package org.hongxi.babi.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import org.hongxi.babi.agent.eventbus.ToolEventBus;
import org.hongxi.babi.agent.middleware.ContextTruncateMiddleware;
import org.hongxi.babi.agent.middleware.ToolNotificationMiddleware;
import org.hongxi.babi.agent.tool.FetchUrlTool;
import org.hongxi.babi.agent.tool.GitHubApiTool;
import org.hongxi.babi.agent.tool.HttpRequestTool;
import org.hongxi.babi.agent.tool.SkillTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web API for the Babi Agent (WebFlux).
 *
 * <p>Uses {@link HarnessAgent} (thread-safe singleton) instead of raw ReActAgent.
 * Each request creates a new {@link RuntimeContext} for session isolation.
 *
 * <p>HarnessAgent provides built-in tools:
 * <ul>
 *   <li>{@code read_file} / {@code write_file} / {@code edit_file} / {@code grep_files} — filesystem operations</li>
 *   <li>{@code execute} — shell command execution</li>
 *   <li>Memory tools (memory_save / memory_search / memory_get)</li>
 *   <li>Workspace context injection (AGENTS.md, MEMORY.md, KNOWLEDGE.md)</li>
 *   <li>Context compaction and tool result eviction</li>
 * </ul>
 *
 * <p>Custom babi tools registered on top:
 * <ul>
 *   <li>{@code fetch_url} — web page fetching</li>
 *   <li>{@code http_request} — HTTP API calls</li>
 *   <li>{@code github_api_request} — GitHub REST API</li>
 *   <li>{@code list_skills} / {@code use_skill} — babi skill system</li>
 * </ul>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/chat/stream} — SSE streaming (form params: message, sessionId)</li>
 *   <li>{@code GET /api/chat/send} — synchronous reply (form params: message, sessionId)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
public class BabiAgentController {

    private static final Logger log = LoggerFactory.getLogger(BabiAgentController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HarnessAgent agent;
    private final AgentStateStore stateStore;
    private final ToolEventBus toolEventBus;

    public BabiAgentController(
            @Value("${babi.agent.workspace:~/babi-workspace}") String workspace,
            @Value("${agentscope.model.name:qwen-plus}") String modelName) {
        String resolvedWorkspace = AgentUtils.resolveWorkspace(workspace);
        Path workspacePath = Path.of(resolvedWorkspace);

        // Ensure workspace directory exists
        try {
            Files.createDirectories(workspacePath);
        } catch (Exception e) {
            log.warn("Failed to create workspace directory: {}", resolvedWorkspace, e);
        }

        // Initialize AGENTS.md in workspace if not present (Harness workspace context)
        initWorkspaceAgentsMd(workspacePath);

        // Session store
        Path sessionPath = Paths.get(System.getProperty("user.home"), ".babi", "sessions");
        this.stateStore = new JsonFileAgentStateStore(sessionPath);
        log.info("Session store initialized at: {}", sessionPath);
        log.info("Agent workspace: {}", resolvedWorkspace);

        // Tool event bus for frontend notifications
        this.toolEventBus = new ToolEventBus();
        log.info("ToolEventBus initialized");

        // Build HarnessAgent singleton (thread-safe, reusable across sessions)
        this.agent = buildHarnessAgent(workspacePath, modelName);
    }

    private HarnessAgent buildHarnessAgent(Path workspacePath, String modelName) {
        // Register babi-specific custom tools (HarnessAgent provides read_file/edit_file/execute/grep natively)
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new FetchUrlTool());
        toolkit.registerTool(new HttpRequestTool());
        toolkit.registerTool(new GitHubApiTool());
        SkillTool skillTool = new SkillTool();
        toolkit.registerTool(skillTool);

        // Build system prompt with skills info (workspace context like AGENTS.md is auto-injected by Harness)
        String sysPrompt = SystemPromptBuilder.build(skillTool.getSkills().values());

        return HarnessAgent.builder()
                .name(AgentUtils.AGENT_NAME)
                .sysPrompt(sysPrompt)
                .model(DashScopeChatModel.builder()
                        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                        .modelName(modelName)
                        .stream(true)
                        .enableSearch(true)
                        .build())
                .toolkit(toolkit)
                .workspace(workspacePath)
                .filesystem(new LocalFilesystemSpec().project(workspacePath))
                .stateStore(stateStore)
                .maxIters(20)
                .enableTaskList()
                .disableDynamicSkills()       // We use our own SkillTool for ~/.agents/skills/ and ~/.babi/skills/
                .disableMemoryTools()          // Not needed for now
                .disableCompaction()           // We have our own ContextTruncateMiddleware
                .disableToolResultEviction()   // Not needed — keep tool results in context
                .middleware(new ContextTruncateMiddleware(30))
                .middleware(new ToolNotificationMiddleware(toolEventBus))
                .build();
    }

    /**
     * SSE streaming chat endpoint.
     *
     * <p>Merges two reactive streams into one SSE output:
     * <ol>
     *   <li><b>toolEvents</b> — tool call notifications from {@link ToolEventBus},
     *       published by {@link ToolNotificationMiddleware} before each tool execution</li>
     *   <li><b>agentEvents</b> — text deltas and tool results from {@code agent.streamEvents()}</li>
     * </ol>
     *
     * @param message   user message
     * @param sessionId session identifier (defaults to "default")
     * @return SSE event stream
     */
    @GetMapping(path = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> streamChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .build();

        // 1. 工具事件流 — 由 ToolNotificationMiddleware 发布到 ToolEventBus
        Flux<ServerSentEvent<String>> toolEvents = Flux.defer(() ->
                toolEventBus.subscribe(sessionId)
                        .map(event -> {
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("type", "tool_call");
                            data.put("tool", event.toolName() != null ? event.toolName() : "unknown");
                            if (event.data() != null) {
                                data.put("toolInput", toJson(event.data()));
                            }
                            return sse("tool_call", data);
                        })
        );

        // 2. Agent 执行流 — 文本 delta + 工具结果
        Flux<ServerSentEvent<String>> agentEvents = Flux.<ServerSentEvent<String>>create(sink -> {
            try {
                Msg userMsg = new UserMessage(message);
                for (AgentEvent event : agent.streamEvents(userMsg, ctx).toIterable()) {
                    if (event instanceof TextBlockDeltaEvent e) {
                        sink.next(sse("token", Map.of("type", "token", "data", e.getDelta())));
                    } else if (event instanceof ToolResultEndEvent e) {
                        sink.next(sse("tool_result", Map.of(
                                "type", "tool_result",
                                "tool", e.getToolCallName(),
                                "state", e.getState().name())));
                    }
                }
                sink.next(sse("done", Map.of("type", "done")));
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());

        // 3. 合并两路流 — Agent 完成时 toolEvents 也自动结束
        return Flux.merge(
                toolEvents.takeUntilOther(agentEvents.then()),
                agentEvents
        );
    }

    /**
     * Synchronous chat endpoint.
     *
     * @param message   user message
     * @param sessionId session identifier (defaults to "default")
     * @return agent's full reply text
     */
    @GetMapping("/send")
    public Mono<String> sendChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .build();
        Msg userMsg = new UserMessage(message);
        return agent.call(userMsg, ctx)
                .map(reply -> reply.getTextContent() != null ? reply.getTextContent() : "");
    }

    /**
     * Delete a session and its persisted state.
     *
     * @param sessionId session identifier to delete
     * @return result message
     */
    @DeleteMapping("/session")
    public Mono<Map<String, String>> deleteSession(
            @RequestParam String sessionId) {
        stateStore.delete(null, sessionId);
        log.info("Session deleted: {}", sessionId);
        return Mono.just(Map.of("status", "ok", "message", "Session '" + sessionId + "' deleted"));
    }

    /**
     * Initialize AGENTS.md in the workspace directory if it doesn't exist.
     * Copies from classpath resource or creates a default one.
     */
    private void initWorkspaceAgentsMd(Path workspacePath) {
        Path agentsMd = workspacePath.resolve("AGENTS.md");
        if (Files.exists(agentsMd)) {
            return;
        }
        try {
            // Try loading from classpath resource
            try (InputStream is = getClass().getResourceAsStream("/workspace/AGENTS.md")) {
                if (is != null) {
                    Files.copy(is, agentsMd, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Initialized AGENTS.md from classpath resource");
                    return;
                }
            }
            // Fallback: create a minimal AGENTS.md
            String defaultContent = """
                    # BabiAgent
                    
                    You are BabiAgent, an expert coding assistant powered by AgentScope Java.
                    
                    ## Rules
                    
                    - When the user provides a URL, ALWAYS call fetch_url FIRST before responding
                    - For GitHub URLs, use github_api_request (NOT fetch_url)
                    - NEVER fabricate content from resources you have not accessed via tool
                    - Be cautious with destructive commands (rm, etc.)
                    - IMAGE OUTPUT: Wrap image URLs in Markdown syntax for inline rendering
                    """;
            Files.writeString(agentsMd, defaultContent);
            log.info("Created default AGENTS.md in workspace");
        } catch (IOException e) {
            log.warn("Failed to initialize AGENTS.md: {}", e.getMessage());
        }
    }

    private static ServerSentEvent<String> sse(String eventType, Object data) {
        return ServerSentEvent.<String>builder()
                .event(eventType)
                .data(toJson(data))
                .build();
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}

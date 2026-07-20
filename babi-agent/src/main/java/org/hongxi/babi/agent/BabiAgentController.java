package org.hongxi.babi.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import org.hongxi.babi.agent.eventbus.ToolEventBus;
import org.hongxi.babi.agent.middleware.ContextTruncateMiddleware;
import org.hongxi.babi.agent.middleware.ToolNotificationMiddleware;
import org.hongxi.babi.agent.tool.CodeSearchTool;
import org.hongxi.babi.agent.tool.FetchUrlTool;
import org.hongxi.babi.agent.tool.FileEditTool;
import org.hongxi.babi.agent.tool.FileReadTool;
import org.hongxi.babi.agent.tool.GitHubApiTool;
import org.hongxi.babi.agent.tool.HttpRequestTool;
import org.hongxi.babi.agent.tool.ShellCommandTool;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web API for the Babi Agent (WebFlux).
 *
 * <p>Provides both SSE streaming and synchronous chat endpoints.
 * Each request creates a new {@link ReActAgent} instance (ReActAgent is NOT thread-safe),
 * sharing the same {@link AgentStateStore} for session persistence.
 *
 * <p>The SSE streaming endpoint uses {@code Flux.merge()} to combine two event sources:
 * <ul>
 *   <li>Tool call events from {@link ToolEventBus} (published by {@link ToolNotificationMiddleware})</li>
 *   <li>Text token events from {@code agent.streamEvents()}</li>
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

    private final AgentStateStore stateStore;
    private final String workspace;
    private final ToolEventBus toolEventBus;

    public BabiAgentController(
            @Value("${babi.agent.workspace:~/babi-workspace}") String workspace) {
        this.workspace = AgentConstants.resolveWorkspace(workspace);
        // Ensure workspace directory exists
        try {
            Files.createDirectories(Path.of(this.workspace));
        } catch (Exception e) {
            log.warn("Failed to create workspace directory: {}", this.workspace, e);
        }
        Path sessionPath = Paths.get(System.getProperty("user.home"), ".babi", "sessions");
        this.stateStore = new JsonFileAgentStateStore(sessionPath);
        log.info("Session store initialized at: {}", sessionPath);
        log.info("Agent workspace: {}", this.workspace);
        this.toolEventBus = new ToolEventBus();
        log.info("ToolEventBus initialized");
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
     * <p>When the agent completes, both streams complete together via {@code Flux.merge()}.
     *
     * <p>Use curl to test:
     * <pre>
     * {@code curl -N -G "http://localhost:8900/api/chat/stream" --data-urlencode "message=帮我执行命令pwd"}
     * </pre>
     *
     * @param message   user message
     * @param sessionId session identifier (defaults to "default")
     * @return SSE event stream
     */
    @GetMapping(path = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> streamChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {

        // 1. 工具事件流 — 由 ToolNotificationMiddleware 发布到 ToolEventBus
        //    通过 takeUntilOther(agentFlux) 在 Agent 完成时自动结束
        Flux<ServerSentEvent<String>> toolEvents = Flux.defer(() ->
                toolEventBus.subscribe(sessionId)
                        .map(event -> {
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("type", "tool_call");
                            data.put("toolName", event.toolName());
                            if (event.data() != null) {
                                data.put("toolInput", toJson(event.data()));
                            }
                            return sse("tool_call", data);
                        })
        );

        // 2. Agent 执行流 — 文本 delta + 工具结果
        //    streamEvents() 是阻塞迭代，放到 boundedElastic 调度器避免阻塞事件循环
        Flux<ServerSentEvent<String>> agentEvents = Flux.<ServerSentEvent<String>>create(sink -> {
            try {
                ReActAgent agent = buildAgent(sessionId);
                Msg userMsg = new UserMessage(message);
                for (AgentEvent event : agent.streamEvents(userMsg).toIterable()) {
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
        ReActAgent agent = buildAgent(sessionId);
        Msg userMsg = new UserMessage(message);
        return agent.call(userMsg)
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

    private ReActAgent buildAgent(String sessionId) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new FileReadTool());
        toolkit.registerTool(new FileEditTool());
        toolkit.registerTool(new ShellCommandTool(workspace));
        toolkit.registerTool(new FetchUrlTool());
        toolkit.registerTool(new HttpRequestTool());
        toolkit.registerTool(new GitHubApiTool());
        toolkit.registerTool(new CodeSearchTool());
        SkillTool skillTool = new SkillTool();
        toolkit.registerTool(skillTool);

        // Inject loaded skills into system prompt so the agent knows what's available
        String sysPrompt = SystemPromptBuilder.build(workspace, skillTool.getSkills().values());

        return ReActAgent.builder()
                .name(AgentConstants.AGENT_NAME)
                .sysPrompt(sysPrompt)
                .model(AgentConstants.createModel())
                .toolkit(toolkit)
                .stateStore(stateStore)
                .defaultSessionId(sessionId)
                .maxIters(20)
                .enableTaskList()
                .middleware(new ContextTruncateMiddleware(30))
                .middleware(new ToolNotificationMiddleware(toolEventBus))
                .build();
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

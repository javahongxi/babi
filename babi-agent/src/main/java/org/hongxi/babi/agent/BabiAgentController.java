package org.hongxi.babi.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import org.hongxi.babi.agent.middleware.ContextTruncateMiddleware;
import org.hongxi.babi.agent.tool.CodeSearchTool;
import org.hongxi.babi.agent.tool.FetchUrlTool;
import org.hongxi.babi.agent.tool.FileEditTool;
import org.hongxi.babi.agent.tool.FileReadTool;
import org.hongxi.babi.agent.tool.GitHubApiTool;
import org.hongxi.babi.agent.tool.HttpRequestTool;
import org.hongxi.babi.agent.tool.ShellCommandTool;
import org.hongxi.babi.agent.tool.SkillTool;
import org.hongxi.babi.agent.tool.TodoWriteTool;
import org.hongxi.babi.agent.tool.WebSearchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hongxi.babi.agent.AgentConstants.SYSTEM_PROMPT;

/**
 * Web API for the Babi Agent.
 *
 * <p>Provides both SSE streaming and synchronous chat endpoints.
 * Each request creates a new {@link ReActAgent} instance (ReActAgent is NOT thread-safe),
 * sharing the same {@link AgentStateStore} for session persistence.
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
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private final AgentStateStore stateStore;

    public BabiAgentController() {
        Path sessionPath = Paths.get(System.getProperty("user.home"), ".babi", "sessions");
        this.stateStore = new JsonFileAgentStateStore(sessionPath);
        log.info("Session store initialized at: {}", sessionPath);
    }

    /**
     * SSE streaming chat endpoint.
     *
     * <p>Streams text deltas, tool calls, and tool results as Server-Sent Events.
     * <p>Use curl to test:
     * <p>
     *     中文消息需要使用 --data-urlencode 让 curl 自动进行 URL 编码，直接拼在 URL 里会导致 400 错误。
     *     -N 参数禁用缓冲，确保实时看到流式输出。
     * </p>
     * <pre>
     * {@code curl -N -G "http://localhost:8900/api/chat/stream" --data-urlencode "message=帮我执行命令pwd"}
     * </pre>
     *
     * @param message   user message
     * @param sessionId session identifier (defaults to "default")
     * @return SSE emitter
     */
    @GetMapping(path = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter streamChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {

        SseEmitter emitter = new SseEmitter(0L); // no timeout

        EXECUTOR.execute(() -> {
            try {
                ReActAgent agent = buildAgent(sessionId);
                Msg userMsg = new UserMessage(message);

                agent.streamEvents(userMsg)
                        .toIterable()
                        .forEach(event -> {
                            try {
                                if (event instanceof TextBlockDeltaEvent e) {
                                    emitter.send(sse("token", Map.of(
                                            "type", "token",
                                            "data", e.getDelta())));
                                } else if (event instanceof ToolCallStartEvent e) {
                                    emitter.send(sse("tool_call", Map.of(
                                            "type", "tool_call",
                                            "tool", e.getToolCallName())));
                                } else if (event instanceof ToolResultEndEvent e) {
                                    emitter.send(sse("tool_result", Map.of(
                                            "type", "tool_result",
                                            "tool", e.getToolCallName(),
                                            "state", e.getState().name())));
                                }
                            } catch (Exception ex) {
                                log.warn("SSE send error: {}", ex.getMessage());
                            }
                        });

                emitter.send(sse("done", Map.of("type", "done")));
                emitter.complete();
            } catch (Exception e) {
                log.error("Stream chat error", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Synchronous chat endpoint.
     *
     * @param message   user message
     * @param sessionId session identifier (defaults to "default")
     * @return agent's full reply text
     */
    @GetMapping("/send")
    public String sendChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {
        ReActAgent agent = buildAgent(sessionId);
        Msg userMsg = new UserMessage(message);
        Msg reply = agent.call(userMsg).block();
        return reply.getTextContent() != null ? reply.getTextContent() : "";
    }

    /**
     * Delete a session and its persisted state.
     *
     * @param sessionId session identifier to delete
     * @return result message
     */
    @DeleteMapping("/session")
    public Map<String, String> deleteSession(
            @RequestParam String sessionId) {
        stateStore.delete(null, sessionId);
        log.info("Session deleted: {}", sessionId);
        return Map.of("status", "ok", "message", "Session '" + sessionId + "' deleted");
    }

    private ReActAgent buildAgent(String sessionId) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new FileReadTool());
        toolkit.registerTool(new FileEditTool());
        toolkit.registerTool(new ShellCommandTool());
        toolkit.registerTool(new FetchUrlTool());
        toolkit.registerTool(new WebSearchTool());
        toolkit.registerTool(new HttpRequestTool());
        toolkit.registerTool(new GitHubApiTool());
        toolkit.registerTool(new CodeSearchTool());
        toolkit.registerTool(new TodoWriteTool());
        toolkit.registerTool(new SkillTool());

        return ReActAgent.builder()
                .name("BabiAgent")
                .sysPrompt(SYSTEM_PROMPT)
                .model("dashscope:qwen-plus")
                .toolkit(toolkit)
                .stateStore(stateStore)
                .defaultSessionId(sessionId)
                .maxIters(20)
                .middleware(new ContextTruncateMiddleware(30))
                .build();
    }

    private static SseEmitter.SseEventBuilder sse(String eventType, Object data) {
        String json;
        try {
            json = MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            json = "{\"type\":\"" + eventType + "\"}";
        }
        return SseEmitter.event().name(eventType).data(json);
    }

}

package org.hongxi.babi.codingagent;

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
import org.hongxi.babi.codingagent.tool.FileReadTool;
import org.hongxi.babi.codingagent.tool.ShellCommandTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Web API for the Coding Agent.
 *
 * <p>Provides both SSE streaming and synchronous chat endpoints.
 * Each request creates a new {@link ReActAgent} instance (ReActAgent is NOT thread-safe),
 * sharing the the same {@link AgentStateStore} for session persistence.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/chat/stream} — SSE streaming (form params: message, sessionId)</li>
 *   <li>{@code POST /api/chat/send} — synchronous reply (JSON body)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
public class CodingAgentController {

    private static final Logger log = LoggerFactory.getLogger(CodingAgentController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private static final String SYSTEM_PROMPT = """
            You are BabiCodingAgent, an expert coding assistant powered by AgentScope Java.
            
            You have access to the following tools:
            - read_file: Read the contents of a file at a given path
            - shell_command: Execute a shell command on the local system
            
            Your capabilities:
            1. Read and analyze source code files
            2. Execute shell commands for build, test, and deployment tasks
            3. Provide code review suggestions
            4. Help debug issues by reading logs and executing diagnostic commands
            
            Guidelines:
            - Always explain what you're doing before executing commands
            - Be cautious with destructive commands (rm, etc.)
            - When reading code, provide clear analysis and suggestions
            - Use shell commands for tasks like compiling, running tests, checking git status
            - If a task is unclear, ask for clarification before proceeding
            """;

    private final AgentStateStore stateStore;

    public CodingAgentController() {
        Path sessionPath = Paths.get(System.getProperty("user.home"), ".babi", "sessions");
        this.stateStore = new JsonFileAgentStateStore(sessionPath);
        log.info("Session store initialized at: {}", sessionPath);
    }

    /**
     * SSE streaming chat endpoint.
     *
     * <p>Streams text deltas, tool calls, and tool results as Server-Sent Events.
     * <p>Use curl to test: {@code curl -N "http://localhost:8082/api/chat/stream?message=hello"}
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
                                    emitter.send(sse("token", Map.of("type", "token", "data", e.getDelta())));
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

    private ReActAgent buildAgent(String sessionId) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new FileReadTool());
        toolkit.registerTool(new ShellCommandTool());

        return ReActAgent.builder()
                .name("BabiCodingAgent")
                .sysPrompt(SYSTEM_PROMPT)
                .model("dashscope:qwen-plus")
                .toolkit(toolkit)
                .stateStore(stateStore)
                .defaultSessionId(sessionId)
                .maxIters(20)
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

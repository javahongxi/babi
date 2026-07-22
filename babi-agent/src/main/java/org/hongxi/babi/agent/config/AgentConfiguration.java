package org.hongxi.babi.agent.config;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import org.hongxi.babi.agent.eventbus.ToolEventBus;
import org.hongxi.babi.agent.middleware.ContextTruncateMiddleware;
import org.hongxi.babi.agent.middleware.ToolNotificationMiddleware;
import org.hongxi.babi.agent.prompt.CodingSystemPrompt;
import org.hongxi.babi.agent.tool.FetchUrlTool;
import org.hongxi.babi.agent.tool.GitHubApiTool;
import org.hongxi.babi.agent.tool.HttpRequestTool;
import org.hongxi.babi.agent.tool.SkillTool;
import org.hongxi.babi.agent.util.AgentUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Spring configuration that assembles the Agent infrastructure.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Resolve and prepare the workspace directory</li>
 *   <li>Create the {@link AgentStateStore} for session persistence</li>
 *   <li>Create the {@link ToolEventBus} for frontend tool-call notifications</li>
 *   <li>Build the {@link HarnessAgent} singleton with tools, middleware, and model</li>
 * </ul>
 *
 * <p>The controller only consumes these beans — it does not know how to build an agent.
 */
@Configuration
public class AgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentConfiguration.class);

    /**
     * Resolved workspace directory path, available for other beans that need it.
     */
    @Bean
    public Path workspacePath(@Value("${babi.agent.workspace:~/babi-workspace}") String workspace) {
        String resolved = AgentUtils.resolveWorkspace(workspace);
        Path path = Path.of(resolved);

        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            log.warn("Failed to create workspace directory: {}", resolved, e);
        }

        AgentUtils.initAgentsMd(path);
        log.info("Agent workspace: {}", resolved);
        return path;
    }

    /**
     * Session state store — persists agent conversation state to JSON files
     * under {@code ~/.babi/sessions/}.
     */
    @Bean
    public AgentStateStore agentStateStore() {
        Path sessionPath = Paths.get(System.getProperty("user.home"), ".babi", "sessions");
        log.info("Session store initialized at: {}", sessionPath);
        return new JsonFileAgentStateStore(sessionPath);
    }

    /**
     * Event bus for broadcasting tool-call events to the frontend via SSE.
     */
    @Bean
    public ToolEventBus toolEventBus() {
        log.info("ToolEventBus initialized");
        return new ToolEventBus();
    }

    /**
     * The core HarnessAgent singleton — thread-safe, reusable across sessions.
     *
     * <p>Registers babi-specific custom tools on top of HarnessAgent's built-in
     * filesystem/shell tools, and configures middleware for context truncation
     * and tool-call notifications.
     */
    @Bean
    public HarnessAgent harnessAgent(
            Path workspacePath,
            AgentStateStore stateStore,
            ToolEventBus toolEventBus,
            @Value("${agentscope.model.name:qwen-plus}") String modelName) {

        // Register babi-specific custom tools
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new FetchUrlTool());
        toolkit.registerTool(new HttpRequestTool());
        toolkit.registerTool(new GitHubApiTool());
        SkillTool skillTool = new SkillTool();
        toolkit.registerTool(skillTool);

        // Build system prompt with skills info
        String sysPrompt = CodingSystemPrompt.build(skillTool.getSkills().values());

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
                .disableMemoryHooks()          // Disable MemoryFlush + MemoryMaintenance middleware
                .disableCompaction()           // We have our own ContextTruncateMiddleware
                .disableToolResultEviction()   // Not needed — keep tool results in context
                .enableAgentTracingLog(false)  // Disable AgentTraceMiddleware for performance
                .middleware(new ContextTruncateMiddleware(30))
                .middleware(new ToolNotificationMiddleware(toolEventBus))
                .build();
    }
}

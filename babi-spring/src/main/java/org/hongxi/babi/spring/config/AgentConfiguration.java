package org.hongxi.babi.spring.config;

import org.hongxi.babi.common.prompt.CodingSystemPrompt;
import org.hongxi.babi.common.util.AgentUtils;
import org.hongxi.babi.spring.advisor.NotifyingToolCallingManager;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.spring.advisor.ToolCallObservationAdvisor;
import org.hongxi.babi.spring.tool.CodeSearchTool;
import org.hongxi.babi.spring.tool.FetchUrlTool;
import org.hongxi.babi.spring.tool.FileEditTool;
import org.hongxi.babi.spring.tool.FileReadTool;
import org.hongxi.babi.spring.tool.GitHubApiTool;
import org.hongxi.babi.spring.tool.HttpRequestTool;
import org.hongxi.babi.spring.tool.ShellCommandTool;
import org.hongxi.babi.spring.tool.SkillTool;
import org.hongxi.babi.spring.tool.WebSearchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Spring configuration that assembles the Spring AI 2.0 infrastructure.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Resolve and prepare the workspace directory</li>
 *   <li>Create tool instances and register them with ChatClient</li>
 *   <li>Configure ChatMemory for session persistence</li>
 *   <li>Build the {@link ChatClient} singleton with system prompt and tools</li>
 *   <li>Create the {@link ToolEventBus} for frontend tool-call notifications</li>
 * </ul>
 *
 * <p>A custom {@link NotifyingToolCallingManager} wraps the default ToolCallingManager
 * to publish tool-call events to the {@link ToolEventBus} before execution,
 * replacing AgentScope's {@code ToolNotificationMiddleware}.
 */
@Configuration
public class AgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentConfiguration.class);

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

    @Bean
    public ToolEventBus toolEventBus() {
        log.info("ToolEventBus initialized");
        return new ToolEventBus();
    }

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(50)
                .build();
    }

    /**
     * Custom {@link ToolCallingManager} that wraps the default implementation
     * to publish tool-call events to the {@link ToolEventBus} before execution.
     */
    @Bean
    public ToolCallingManager toolCallingManager(
            ToolCallbackResolver toolCallbackResolver,
            ToolEventBus toolEventBus) {
        ToolCallingManager defaultManager = ToolCallingManager.builder()
                .toolCallbackResolver(toolCallbackResolver)
                .build();
        return new NotifyingToolCallingManager(defaultManager, toolEventBus);
    }

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder chatClientBuilder,
            ChatMemory chatMemory,
            ToolCallingManager toolCallingManager,
            Path workspacePath) {
        SkillTool skillTool = new SkillTool(workspacePath);

        String sysPrompt = CodingSystemPrompt.build(
                workspacePath.toString(),
                skillTool.getSkills().values());

        // Spring AI does not inject runtime state like AgentScope does,
        // so append the current date/time to the system prompt explicitly.
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        sysPrompt += "\n\nCurrent date and time: " + now;

        return chatClientBuilder
                .defaultSystem(sysPrompt)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        ToolCallingAdvisor.builder()
                                .toolCallingManager(toolCallingManager)
                                .build(),
                        new ToolCallObservationAdvisor()
                )
                .defaultTools(
                        new FetchUrlTool(),
                        new HttpRequestTool(),
                        new GitHubApiTool(),
                        new FileReadTool(),
                        new FileEditTool(),
                        new ShellCommandTool(workspacePath.toString()),
                        new CodeSearchTool(),
                        new WebSearchTool(),
                        skillTool
                )
                .build();
    }
}

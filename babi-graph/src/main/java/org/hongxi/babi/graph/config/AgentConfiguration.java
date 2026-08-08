package org.hongxi.babi.graph.config;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.agent.Agent;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.hongxi.babi.common.config.AgentProperties;
import org.hongxi.babi.common.config.DashScopeProperties;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.common.prompt.CodingSystemPrompt;
import org.hongxi.babi.graph.hook.ToolNotificationEdgeHook;
import org.hongxi.babi.graph.model.DashScopeChatModel;
import org.hongxi.babi.graph.tool.*;
import org.hongxi.babi.common.util.AgentUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;

/**
 * Spring configuration that assembles the LangGraph4J Agent infrastructure.
 */
@Configuration
@EnableConfigurationProperties({AgentProperties.class, DashScopeProperties.class})
public class AgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentConfiguration.class);

    @Bean
    public Path workspacePath(AgentProperties agentProperties) {
        String resolved = AgentUtils.resolveWorkspace(agentProperties.workspace());
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
    public MemorySaver memorySaver() {
        return new MemorySaver();
    }

    @Bean
    public StreamingChatModel streamingChatModel(DashScopeProperties properties) {
        return new DashScopeChatModel(
                properties.apiKey(),
                properties.chat().model(),
                properties.chat().temperature(),
                properties.chat().topP(),
                properties.chat().enableSearch());
    }

    @Bean
    public CompiledGraph<?> compiledGraph(
            Path workspacePath,
            ToolEventBus toolEventBus,
            MemorySaver memorySaver,
            StreamingChatModel streamingChatModel,
            AgentProperties agentProperties,
            DashScopeProperties properties) throws GraphStateException {
        // Create tool instances
        List<Object> tools = new LinkedList<>();
        var skillTool = new SkillTool(workspacePath);
        tools.add(skillTool);
        tools.add(new FetchUrlTool());
        tools.add(new HttpRequestTool());
        tools.add(new GitHubApiTool());
        tools.add(new FileReadTool());
        tools.add(new FileEditTool());
        tools.add(new ShellCommandTool(workspacePath.toString()));
        tools.add(new CodeSearchTool());
        tools.add(new GlobTool());
        if (agentProperties.enableTaskList()) {
            tools.add(new TodoWriteTool());
        }
        // When DashScope native search is enabled, skip the external WebSearchTool
        if (!properties.chat().enableSearch()) {
            tools.add(new WebSearchTool());
        }

        // Register ImageGenerationTool if image model is configured
        if (properties.image() != null && properties.image().model() != null) {
            tools.add(new ImageGenerationTool(
                    properties.apiKey(),
                    properties.image().model(),
                    properties.image().promptExtend()));
        }

        // Build system prompt
        String sysPrompt = CodingSystemPrompt.build(
                workspacePath.toString(),
                skillTool.getSkills().values(),
                properties.chat().enableSearch(),
                agentProperties.enableTaskList());

        // LangGraph4J does not inject runtime state like AgentScope does,
        // so append the current date/time to the system prompt explicitly.
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        sysPrompt += "\n\nCurrent date and time: " + now;

        // Build the agent graph
        var graph = AgentExecutor.builder()
                .chatModel(streamingChatModel)
                .systemMessage(SystemMessage.from(sysPrompt))
                .toolsFromObject(tools.toArray())
                .build();

        // Register tool-call notification hook on the "action" edge
        graph.addWrapCallEdgeHook(Agent.ACTION_LABEL, new ToolNotificationEdgeHook(toolEventBus));

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(memorySaver)
                .recursionLimit(50)
                .build();

        return graph.compile(compileConfig);
    }
}

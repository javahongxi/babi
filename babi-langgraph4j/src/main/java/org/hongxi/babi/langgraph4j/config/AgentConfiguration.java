package org.hongxi.babi.langgraph4j.config;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.common.prompt.CodingSystemPrompt;
import org.hongxi.babi.langgraph4j.tool.*;
import org.hongxi.babi.common.util.AgentUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Spring configuration that assembles the LangGraph4J Agent infrastructure.
 */
@Configuration
public class AgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentConfiguration.class);

    @Value("${babi.agent.model.name:qwen-plus}")
    private String modelName;

    @Value("${babi.agent.model.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${babi.agent.model.api-key:}")
    private String apiKey;

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
    public MemorySaver memorySaver() {
        return new MemorySaver();
    }

    @Bean
    public CompiledGraph<?> compiledGraph(
            Path workspacePath,
            ToolEventBus toolEventBus,
            MemorySaver memorySaver) throws GraphStateException {

        // DashScope via OpenAI-compatible API
        var streamingModel = OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .build();

        // Create tool instances
        var fetchUrlTool = new FetchUrlTool(toolEventBus);
        var httpRequestTool = new HttpRequestTool(toolEventBus);
        var gitHubApiTool = new GitHubApiTool(toolEventBus);
        var fileReadTool = new FileReadTool(toolEventBus);
        var fileEditTool = new FileEditTool(toolEventBus);
        var shellCommandTool = new ShellCommandTool(workspacePath.toString(), toolEventBus);
        var codeSearchTool = new CodeSearchTool(toolEventBus);
        var skillTool = new SkillTool(workspacePath, toolEventBus);
        var webSearchTool = new WebSearchTool(toolEventBus);

        // Build system prompt
        String sysPrompt = CodingSystemPrompt.build(
                workspacePath.toString(), skillTool.getSkills().values());

        // LangGraph4J does not inject runtime state like AgentScope does,
        // so append the current date/time to the system prompt explicitly.
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        sysPrompt += "\n\nCurrent date and time: " + now;

        // Build and compile the agent graph
        var graph = AgentExecutor.builder()
                .chatModel(streamingModel)
                .systemMessage(SystemMessage.from(sysPrompt))
                .toolsFromObject(fetchUrlTool)
                .toolsFromObject(httpRequestTool)
                .toolsFromObject(gitHubApiTool)
                .toolsFromObject(fileReadTool)
                .toolsFromObject(fileEditTool)
                .toolsFromObject(shellCommandTool)
                .toolsFromObject(codeSearchTool)
                .toolsFromObject(skillTool)
                .toolsFromObject(webSearchTool)
                .build();

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(memorySaver)
                .build();

        log.info("LangGraph4J agent compiled with streaming model: {}", modelName);
        return graph.compile(compileConfig);
    }
}

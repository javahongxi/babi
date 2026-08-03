package org.hongxi.babi.graph.config;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.agent.Agent;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.common.prompt.CodingSystemPrompt;
import org.hongxi.babi.graph.hook.ToolNotificationEdgeHook;
import org.hongxi.babi.graph.tool.*;
import org.hongxi.babi.common.util.AgentUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(Properties.class)
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
    public MemorySaver memorySaver() {
        return new MemorySaver();
    }

    @Bean
    public StreamingChatModel streamingChatModel(Properties properties) {
        ChatModelProperties chatModelProperties = properties.streamingChatModel();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(chatModelProperties.baseUrl())
                .apiKey(chatModelProperties.apiKey())
                .organizationId(chatModelProperties.organizationId())
                .projectId(chatModelProperties.projectId())
                .modelName(chatModelProperties.modelName())
                .temperature(chatModelProperties.temperature())
                .topP(chatModelProperties.topP())
                .stop(chatModelProperties.stop())
                .maxTokens(chatModelProperties.maxTokens())
                .maxCompletionTokens(chatModelProperties.maxCompletionTokens())
                .presencePenalty(chatModelProperties.presencePenalty())
                .frequencyPenalty(chatModelProperties.frequencyPenalty())
                .logitBias(chatModelProperties.logitBias())
                .responseFormat(chatModelProperties.responseFormat())
                .seed(chatModelProperties.seed())
                .user(chatModelProperties.user())
                .strictTools(chatModelProperties.strictTools())
                .parallelToolCalls(chatModelProperties.parallelToolCalls())
                .store(chatModelProperties.store())
                .metadata(chatModelProperties.metadata())
                .serviceTier(chatModelProperties.serviceTier())
                .defaultRequestParameters(OpenAiChatRequestParameters.builder()
                        .reasoningEffort(chatModelProperties.reasoningEffort())
                        .customParameters(chatModelProperties.customParameters())
                        .build())
                .returnThinking(chatModelProperties.returnThinking())
                .timeout(chatModelProperties.timeout())
                .logRequests(chatModelProperties.logRequests())
                .logResponses(chatModelProperties.logResponses())
                .customHeaders(chatModelProperties.customHeaders())
                .customQueryParams(chatModelProperties.customQueryParams())
                .build();
    }

    @Bean
    public CompiledGraph<?> compiledGraph(
            Path workspacePath,
            ToolEventBus toolEventBus,
            MemorySaver memorySaver,
            StreamingChatModel streamingChatModel) throws GraphStateException {
        // Create tool instances
        var fetchUrlTool = new FetchUrlTool();
        var httpRequestTool = new HttpRequestTool();
        var gitHubApiTool = new GitHubApiTool();
        var fileReadTool = new FileReadTool();
        var fileEditTool = new FileEditTool();
        var shellCommandTool = new ShellCommandTool(workspacePath.toString());
        var codeSearchTool = new CodeSearchTool();
        var skillTool = new SkillTool(workspacePath);
        var webSearchTool = new WebSearchTool();

        // Build system prompt
        String sysPrompt = CodingSystemPrompt.build(
                workspacePath.toString(), skillTool.getSkills().values());

        // LangGraph4J does not inject runtime state like AgentScope does,
        // so append the current date/time to the system prompt explicitly.
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        sysPrompt += "\n\nCurrent date and time: " + now;

        // Build the agent graph
        var graph = AgentExecutor.builder()
                .chatModel(streamingChatModel)
                .systemMessage(SystemMessage.from(sysPrompt))
                .toolsFromObject(
                        fetchUrlTool,
                        httpRequestTool,
                        webSearchTool,
                        gitHubApiTool,
                        fileReadTool,
                        fileEditTool,
                        shellCommandTool,
                        codeSearchTool,
                        skillTool
                )
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

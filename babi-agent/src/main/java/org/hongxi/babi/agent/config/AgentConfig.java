package org.hongxi.babi.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.hongxi.babi.agent.tool.BabiTools;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI Alibaba ReactAgent 配置
 * <p>
 * 演示如何使用 ReactAgent 构建智能体，并注册自定义工具
 */
@Configuration
public class AgentConfig {

    private static final String INSTRUCTION = """
            You are a helpful assistant named Babi AI.
            You have access to tools that can help you get current date/time and perform text processing.
            Use these tools to assist users with their tasks.
            Always respond in Chinese.
            """;

    @Bean
    public ReactAgent chatbotReactAgent(ChatModel chatModel,
                                        BabiTools babiTools,
                                        MemorySaver memorySaver) {
        ToolCallbackProvider toolCallbackProvider = MethodToolCallbackProvider.builder()
                .toolObjects(babiTools)
                .build();
        ToolCallback[] tools = toolCallbackProvider.getToolCallbacks();
        return ReactAgent.builder()
                .name("BabiAgent")
                .model(chatModel)
                .instruction(INSTRUCTION)
                .enableLogging(true)
                .saver(memorySaver)
                .tools(tools)
                .build();
    }

    @Bean
    public MemorySaver memorySaver() {
        return new MemorySaver();
    }
}

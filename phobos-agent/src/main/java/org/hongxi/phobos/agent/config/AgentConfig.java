package org.hongxi.phobos.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
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
            You are a helpful assistant named Phobos AI.
            You have access to tools that can help you get current date/time and perform text processing.
            Use these tools to assist users with their tasks.
            Always respond in Chinese.
            """;

    @Bean
    public ReactAgent chatbotReactAgent(ChatModel chatModel,
                                        ToolCallback getCurrentDateTime,
                                        ToolCallback textProcessor,
                                        MemorySaver memorySaver) {
        return ReactAgent.builder()
                .name("PhobosAgent")
                .model(chatModel)
                .instruction(INSTRUCTION)
                .enableLogging(true)
                .saver(memorySaver)
                .tools(getCurrentDateTime, textProcessor)
                .build();
    }

    @Bean
    public MemorySaver memorySaver() {
        return new MemorySaver();
    }

    /**
     * 工具：获取当前日期时间
     */
    @Bean
    public ToolCallback getCurrentDateTime() {
        return FunctionToolCallback.builder("get_current_datetime", (ToolInput input) -> {
            return java.time.LocalDateTime.now().toString();
        })
                .description("获取当前的日期和时间，返回格式为 ISO-8601")
                .inputType(ToolInput.class)
                .build();
    }

    /**
     * 工具：文本处理器（转大写/转小写/反转）
     */
    @Bean
    public ToolCallback textProcessor() {
        return FunctionToolCallback.builder("text_processor", (TextInput input) -> {
            return switch (input.operation()) {
                case "uppercase" -> input.text().toUpperCase();
                case "lowercase" -> input.text().toLowerCase();
                case "reverse" -> new StringBuilder(input.text()).reverse().toString();
                default -> "Unsupported operation: " + input.operation();
            };
        })
                .description("处理文本：支持 uppercase(转大写)、lowercase(转小写)、reverse(反转文本)")
                .inputType(TextInput.class)
                .build();
    }

    /**
     * 无参工具输入（用于不需要参数的工具）
     */
    public record ToolInput() {}

    /**
     * 文本处理工具输入
     */
    public record TextInput(String text, String operation) {}
}

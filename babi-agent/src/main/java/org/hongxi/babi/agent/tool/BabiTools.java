package org.hongxi.babi.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Babi Agent 工具集
 * <p>
 * 提供日期时间查询和文本处理能力，供 AI 智能体调用
 */
@Component
public class BabiTools {

    /**
     * 获取当前日期时间
     */
    @Tool(name = "get_current_datetime", description = "获取当前的日期和时间，返回格式为 ISO-8601")
    public String getCurrentDateTime() {
        return LocalDateTime.now().toString();
    }

    /**
     * 文本处理器（转大写/转小写/反转）
     */
    @Tool(name = "text_processor", description = "处理文本：支持 uppercase(转大写)、lowercase(转小写)、reverse(反转文本)")
    public String textProcessor(@ToolParam(description = "要处理的文本") String text,
                                @ToolParam(description = "操作类型：uppercase/lowercase/reverse") String operation) {
        return switch (operation) {
            case "uppercase" -> text.toUpperCase();
            case "lowercase" -> text.toLowerCase();
            case "reverse" -> new StringBuilder(text).reverse().toString();
            default -> "Unsupported operation: " + operation;
        };
    }
}

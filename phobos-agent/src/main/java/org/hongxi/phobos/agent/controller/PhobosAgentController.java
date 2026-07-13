package org.hongxi.phobos.agent.controller;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.RunnableConfig;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 对话控制器
 * <p>
 * 演示如何通过 REST API 调用 ReactAgent 进行对话
 */
@RestController
@RequestMapping("/api/agent")
public class PhobosAgentController {

    private final ReactAgent reactAgent;

    public PhobosAgentController(ReactAgent reactAgent) {
        this.reactAgent = reactAgent;
    }

    /**
     * 简单对话接口
     *
     * @param message 用户消息
     * @return AI 回复内容
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        try {
            AssistantMessage response = reactAgent.call(message);
            return response.getText();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 带线程的对话接口（支持多轮会话隔离）
     *
     * @param message  用户消息
     * @param threadId 线程ID
     * @return AI 回复内容
     */
    @GetMapping("/chat/thread")
    public String chatWithThread(@RequestParam String message,
                                 @RequestParam(defaultValue = "default") String threadId) {
        try {
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();
            AssistantMessage response = reactAgent.call(message, config);
            return response.getText();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}

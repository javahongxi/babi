package org.hongxi.babi.spring.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Advisor 用于观测和记录 Spring AI 工具调用（Tool Calling）的完整迭代过程。
 *
 * <p>该 Advisor 拦截 {@link CallAdvisor} 链中的每次调用，记录以下关键信息：</p>
 * <ul>
 *   <li>当前迭代轮次（基于消息历史中 {@link ToolResponseMessage} 数量推断）</li>
 *   <li>完整的消息历史，包括系统消息、工具调用请求、工具响应等</li>
 *   <li>模型响应分析：判断是否包含工具调用请求，或已返回最终响应</li>
 *   <li>每轮调用的耗时统计</li>
 * </ul>
 *
 * <p>优先级设为 {@link Ordered#HIGHEST_PRECEDENCE} + 400，确保在 Advisor 链中较早执行，
 * 从而捕获最完整的调用上下文。</p>
 *
 * @author hongxi
 */
public class ToolCallObservationAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ToolCallObservationAdvisor.class);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 400;
    }

    @Override
    public String getName() {
        return "ToolCallObservationAdvisor";
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // 根据消息历史推断当前迭代轮次
        // 每轮工具调用会增加 AssistantMessage(toolCalls) + ToolResponseMessage，共 2 条
        // 初始状态: SYSTEM + USER = 2 条 → 第 1 轮
        // 第 1 轮工具调用后: +2 条 → 第 2 轮
        List<Message> messages = request.prompt().getInstructions();
        long iteration = computeIteration(messages);

        log.info("╔═══════════════════════════════════════════════════════");
        log.info("║ [Advisor 链] 第 {} 轮调用（消息数: {}）", iteration, messages.size());
        log.info("╠═══════════════════════════════════════════════════════");

        // 打印当前消息历史（展示累积的对话上下文）
        logMessageHistory(messages);

        // Next Call
        long startTime = System.currentTimeMillis();
        ChatClientResponse response = chain.nextCall(request);
        long elapsed = System.currentTimeMillis() - startTime;

        // 分析响应内容
        analyzeResponse(response, iteration, elapsed);

        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        List<Message> messages = request.prompt().getInstructions();
        long iteration = computeIteration(messages);

        log.info("╔═══════════════════════════════════════════════════════");
        log.info("║ [Advisor 链-Stream] 第 {} 轮调用（消息数: {}）", iteration, messages.size());
        log.info("╠═══════════════════════════════════════════════════════");

        logMessageHistory(messages);

        long startTime = System.currentTimeMillis();
        return chain.nextStream(request)
                .doOnComplete(() -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.info("║ [Stream 第 {} 轮] 流式响应完成，耗时 {}ms", iteration, elapsed);
                    log.info("╚═══════════════════════════════════════════════════════");
                });
    }

    private long computeIteration(List<Message> messages) {
        // 统计 ToolResponseMessage 的数量即为已完成的工具调用轮次
        long toolResponseCount = messages.stream()
                .filter(m -> m instanceof ToolResponseMessage)
                .count();
        return toolResponseCount + 1;
    }

    private void logMessageHistory(List<Message> messages) {
        log.info("║ 当前消息历史（共 {} 条）:", messages.size());
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            String type = msg.getMessageType().name();
            String content = truncate(msg.getText(), 80);

            if (msg instanceof ToolResponseMessage toolMsg) {
                // 工具响应消息：展示工具名称和返回结果
                log.info("║   [{}] {} (responses={})", i, type, toolMsg.getResponses().size());
                for (ToolResponseMessage.ToolResponse resp : toolMsg.getResponses()) {
                    log.info("║       → tool: {}, result: {}", resp.name(), truncate(resp.responseData(), 60));
                }
            } else if (msg instanceof AssistantMessage assistantMsg && assistantMsg.hasToolCalls()) {
                // AI 工具调用请求：展示要调用的工具
                log.info("║   [{}] {} (toolCalls={})", i, type, assistantMsg.getToolCalls().size());
                assistantMsg.getToolCalls().forEach(tc ->
                        log.info("║       → call: {}({})", tc.name(), truncate(tc.arguments(), 60)));
            } else if (msg instanceof SystemMessage || msg instanceof AssistantMessage) {
                log.info("║   [{}] {}: {}", i, type, truncate(content, 25));
            } else {
                log.info("║   [{}] {}: {}", i, type, content);
            }
        }
        log.info("╚═══════════════════════════════════════════════════════");
    }

    private void analyzeResponse(ChatClientResponse response, long iteration, long elapsed) {
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null) {
            log.info("║ [第 {} 轮] 响应为空，耗时 {}ms", iteration, elapsed);
            return;
        }

        boolean hasToolCalls = chatResponse.getResults().stream()
                .map(Generation::getOutput)
                .anyMatch(AssistantMessage::hasToolCalls);

        if (hasToolCalls) {
            log.info("║ [第 {} 轮] 大模型响应（耗时 {}ms）→ hasToolCalls=true，模型决定调用工具", iteration, elapsed);
        } else {
            log.info("║ [第 {} 轮] 模型返回最终响应（耗时 {}ms）→ 工具调用循环终止", iteration, elapsed);
        }
        log.info("╚═══════════════════════════════════════════════════════");
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
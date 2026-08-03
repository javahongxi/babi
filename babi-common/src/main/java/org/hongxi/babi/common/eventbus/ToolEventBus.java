package org.hongxi.babi.common.eventbus;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;

/**
 * 工具事件总线 - 基于 Reactor Sinks 的发布/订阅机制
 *
 * <p>用于解耦工具调用事件的产生和消费：
 * <ul>
 *   <li>产生端：工具调用时发布事件</li>
 *   <li>消费端：SSE Controller 订阅事件并推送给前端</li>
 * </ul>
 *
 * <p>这是一个单例 Bean，所有 Agent 共享同一个总线，通过 sessionId 隔离不同会话的事件。
 */
public class ToolEventBus {

    private final Sinks.Many<ToolEvent> sink =
            Sinks.many().multicast().onBackpressureBuffer(256, false);

    /**
     * 发布工具事件到总线
     *
     * @param event 工具事件
     */
    public void publish(ToolEvent event) {
        sink.tryEmitNext(event);
    }

    /**
     * 订阅指定会话的工具事件流
     *
     * @param sessionId 会话 ID
     * @return 该会话的工具事件流
     */
    public Flux<ToolEvent> subscribe(String sessionId) {
        return sink.asFlux().filter(e -> sessionId.equals(e.sessionId()));
    }

    /**
     * 工具执行状态枚举
     */
    public enum ToolState {
        /** 工具执行成功 */
        SUCCESS,
        /** 工具执行失败（抛出异常） */
        ERROR,
        /** 工具执行被中断（客户端断开、取消请求等） */
        INTERRUPTED
    }

    /**
     * 工具事件记录
     *
     * @param sessionId 会话 ID
     * @param eventType 事件类型（TOOL_CALL / TOOL_RESULT）
     * @param toolName  工具名称
     * @param data      事件数据（工具输入参数）
     * @param state     工具执行状态，仅 TOOL_RESULT 事件有值
     */
    public record ToolEvent(
            String sessionId,
            String eventType,
            String toolName,
            Map<String, Object> data,
            ToolState state) {

        public ToolEvent(String sessionId, String eventType, String toolName, Map<String, Object> data) {
            this(sessionId, eventType, toolName, data, null);
        }

        /**
         * 创建工具调用事件
         */
        public static ToolEvent toolCall(
                String sessionId,
                String toolName,
                Map<String, Object> input) {
            return new ToolEvent(sessionId, "TOOL_CALL", toolName, input);
        }

        /**
         * 创建工具执行结果事件
         *
         * @param sessionId 会话 ID
         * @param toolName  工具名称
         * @param state     执行状态
         */
        public static ToolEvent toolResult(
                String sessionId,
                String toolName,
                ToolState state) {
            return new ToolEvent(sessionId, "TOOL_RESULT", toolName, null, state);
        }
    }
}

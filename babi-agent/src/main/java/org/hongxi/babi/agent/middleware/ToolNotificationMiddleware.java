package org.hongxi.babi.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import org.hongxi.babi.agent.eventbus.ToolEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import reactor.core.publisher.Flux;

/**
 * 工具通知中间件 - 在工具执行前拦截并发布事件到 ToolEventBus
 *
 * <p>实现 {@link MiddlewareBase} 接口的 {@code onActing} 方法，在 Agent 执行工具前：
 * <ol>
 *   <li>从 RuntimeContext 获取 sessionId</li>
 *   <li>遍历即将执行的所有 tool calls</li>
 *   <li>将每个 tool call 发布到 ToolEventBus</li>
 *   <li>继续执行链（不阻塞工具执行）</li>
 * </ol>
 *
 * <p>这是一个非侵入式中间件，发布事件后立即调用 {@code next.apply(input)} 继续正常执行。
 */
public class ToolNotificationMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(ToolNotificationMiddleware.class);

    private final ToolEventBus bus;

    public ToolNotificationMiddleware(ToolEventBus bus) {
        this.bus = bus;
    }

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext ctx,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {

        String sessionId = resolveSessionId(agent, ctx);

        if (sessionId != null && input.toolCalls() != null) {
            for (ToolUseBlock tu : input.toolCalls()) {
                Map<String, Object> inputData = new LinkedHashMap<>();
                if (tu.getInput() != null) {
                    inputData.putAll(tu.getInput());
                }
                try {
                    bus.publish(ToolEventBus.ToolEvent.toolCall(sessionId, tu.getName(), inputData));
                    log.debug("Published TOOL_CALL event: session={}, tool={}", sessionId, tu.getName());
                } catch (Exception e) {
                    log.debug("Failed to publish tool event for {}: {}", tu.getName(), e.getMessage());
                }
            }
        }

        return next.apply(input);
    }

    private static String resolveSessionId(Agent agent, RuntimeContext ctx) {
        if (ctx == null) return null;
        if (ctx.getSessionId() != null) {
            return ctx.getSessionId();
        }
        return ctx.getUserId();
    }
}

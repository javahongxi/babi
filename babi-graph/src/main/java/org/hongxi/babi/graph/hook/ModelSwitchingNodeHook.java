package org.hongxi.babi.graph.hook;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.hook.NodeHook;
import org.hongxi.babi.common.util.SessionContextHolder;
import org.hongxi.babi.graph.model.DashScopeChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A LangGraph4J {@link NodeHook.WrapCall} around the "agent" (call-model) node that
 * bridges the per-session model override to the thread executing the model call.
 *
 * <p>{@link DashScopeChatModel} resolves the model name for each request from
 * {@link SessionContextHolder} (ThreadLocal), because LangGraph4J's AgentExecutor gives
 * no way to inject per-request {@code ChatRequestParameters}. Since LangGraph4J 1.9.x the
 * graph runs its actions on pool threads, the ThreadLocal set by the submitting thread is
 * not visible there, so this hook re-establishes it right before the model call using the
 * session ID carried by {@link RunnableConfig#threadId()}.
 */
public class ModelSwitchingNodeHook implements NodeHook.WrapCall<AgentExecutor.State> {

    private static final Logger log = LoggerFactory.getLogger(ModelSwitchingNodeHook.class);

    @Override
    public CompletableFuture<Map<String, Object>> applyWrap(String nodeId,
                                                           AgentExecutor.State state,
                                                           RunnableConfig config,
                                                           AsyncNodeActionWithConfig<AgentExecutor.State> action) {
        String sessionId = config.threadId().orElseGet(SessionContextHolder::getSessionId);
        String model = DashScopeChatModel.modelOverrideFor(sessionId);
        if (model != null) {
            SessionContextHolder.setModelOverride(model);
            log.debug("Applied model override '{}' for session={} node={}", model, sessionId, nodeId);
        } else {
            SessionContextHolder.clearModelOverride();
        }

        return action.apply(state, config)
                .whenComplete((result, ex) -> SessionContextHolder.clearModelOverride());
    }
}

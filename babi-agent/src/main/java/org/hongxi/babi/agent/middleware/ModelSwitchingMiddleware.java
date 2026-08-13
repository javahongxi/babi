package org.hongxi.babi.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.EndpointType;
import org.hongxi.babi.common.model.ModelCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Middleware that switches the Model instance per request based on a
 * "modelOverride" attribute in {@link RuntimeContext}.
 *
 * <p>Intercepts {@link MiddlewareBase#onModelCall} — the raw model API call phase
 * in the ReAct loop. When the RuntimeContext carries a "modelOverride" attribute,
 * the corresponding pre-cached {@link DashScopeChatModel} instance replaces the
 * default model in {@link ModelCallInput} before passing to the next middleware.
 *
 * <p>This allows per-request model selection without rebuilding the HarnessAgent
 * or modifying the AgentScope SDK. Model instances are cached in a
 * {@link ConcurrentHashMap} keyed by model name; first use creates the instance,
 * subsequent uses hit the cache.
 *
 * <p>This is consistent with babi's existing middleware pattern
 * ({@link ContextTruncateMiddleware}, {@link ToolNotificationMiddleware}).
 */
public class ModelSwitchingMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(ModelSwitchingMiddleware.class);

    private static final String MODEL_OVERRIDE_KEY = "modelOverride";

    private final String apiKey;
    private final boolean enableSearch;
    private final Map<String, Model> modelCache = new ConcurrentHashMap<>();

    public ModelSwitchingMiddleware(String apiKey, boolean enableSearch) {
        this.apiKey = apiKey;
        this.enableSearch = enableSearch;
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {

        String desiredModel = ctx.get(MODEL_OVERRIDE_KEY);
        if (desiredModel != null && !desiredModel.isBlank()) {
            Model overridden = modelCache.computeIfAbsent(desiredModel, this::createModel);
            log.debug("Switching model to '{}' for session {}", desiredModel, ctx.getSessionId());
            // Reconstruct ModelCallInput with the new Model instance
            ModelCallInput overriddenInput = new ModelCallInput(
                    input.messages(), input.tools(), input.options(), overridden);
            return next.apply(overriddenInput);
        }
        return next.apply(input);
    }

    private Model createModel(String modelName) {
        DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(true)
                .enableSearch(enableSearch);
        if (ModelCatalog.isMultimodalModel(modelName)) {
            builder.endpointType(EndpointType.MULTIMODAL);
        }
        log.info("Created and cached DashScopeChatModel for model '{}'", modelName);
        return builder.build();
    }
}

package org.hongxi.babi.graph.model;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolCallBase;
import com.alibaba.dashscope.tools.ToolCallFunction;
import com.alibaba.dashscope.tools.ToolFunction;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import io.reactivex.Flowable;
import org.hongxi.babi.common.util.DashScopeEndpointUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import reactor.core.publisher.Sinks;

/**
 * DashScope SDK adapter that implements langchain4j's {@link ChatModel} and
 * {@link StreamingChatModel} interfaces, allowing langgraph4j's agent-executor
 * (which is built on langchain4j types) to use DashScope directly.
 *
 * <p>Uses the DashScope {@link MultiModalConversation} API (multimodal-generation
 * endpoint) to support both text-only and multimodal models like qwen3.8-max.
 *
 * <p>Supports:
 * <ul>
 *   <li>Synchronous chat via {@link #doChat(ChatRequest)}</li>
 *   <li>Streaming chat via {@link #doChat(ChatRequest, StreamingChatResponseHandler)}</li>
 *   <li>Tool calling with proper specification conversion</li>
 *   <li>Streaming tool call accumulation (incremental output mode)</li>
 *   <li>Reasoning/thinking content streaming</li>
 *   <li>Native search (enable_search)</li>
 * </ul>
 */
public class DashScopeChatModel implements ChatModel, StreamingChatModel {

    private static final Logger log = LoggerFactory.getLogger(DashScopeChatModel.class);
    private static final Gson GSON = new Gson();

    /** Per-session sinks for thinking content, set by BabiService before graph execution */
    private static final Map<String, Sinks.Many<String>> THINKING_SINKS = new ConcurrentHashMap<>();

    /** Per-session sinks for text token content, set by BabiService before graph execution */
    private static final Map<String, Sinks.Many<String>> TEXT_SINKS = new ConcurrentHashMap<>();

    /**
     * Register a thinking sink for a session.
     * Must be called before graph.stream() for that session.
     */
    public static void registerThinkingSink(String sessionId, Sinks.Many<String> sink) {
        THINKING_SINKS.put(sessionId, sink);
    }

    /**
     * Register a text token sink for a session.
     * Must be called before graph.stream() for that session.
     */
    public static void registerTextSink(String sessionId, Sinks.Many<String> sink) {
        TEXT_SINKS.put(sessionId, sink);
    }

    /**
     * Unregister all sinks for a session.
     */
    public static void unregisterSinks(String sessionId) {
        THINKING_SINKS.remove(sessionId);
        TEXT_SINKS.remove(sessionId);
    }

    private final String apiKey;
    private final String model;
    private final Double temperature;
    private final Double topP;
    private final boolean enableSearch;
    private final boolean multimodal;
    private final MultiModalConversation conversation;  // for multimodal models
    private final Generation generation;                 // for text-only models

    public DashScopeChatModel(String apiKey,
                              String model,
                              Double temperature,
                              Double topP,
                              boolean enableSearch) {
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.topP = topP;
        this.enableSearch = enableSearch;
        this.multimodal = DashScopeEndpointUtil.isMultimodalModel(model);
        this.conversation = multimodal ? new MultiModalConversation() : null;
        this.generation = multimodal ? null : new Generation();
        log.info("DashScope model '{}' routed to {} API", model, multimodal ? "multimodal" : "text-generation");
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of();
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OTHER;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return List.of();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return DefaultChatRequestParameters.EMPTY;
    }

    // =========================================================================
    // ChatModel (synchronous)
    // =========================================================================

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        if (multimodal) {
            List<MultiModalMessage> messages = convertMultiModalMessages(chatRequest.messages());
            MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> builder =
                    buildMultiModalParam(messages, chatRequest, false);
            try {
                MultiModalConversationResult result = conversation.call(builder.build());
                return convertMultiModalToChatResponse(result);
            } catch (Exception e) {
                throw new RuntimeException("DashScope API call failed", e);
            }
        } else {
            List<Message> messages = convertTextMessages(chatRequest.messages());
            GenerationParam.GenerationParamBuilder<?, ?> builder =
                    buildTextParam(messages, chatRequest, false);
            try {
                GenerationResult result = generation.call(builder.build());
                return convertTextToChatResponse(result);
            } catch (Exception e) {
                throw new RuntimeException("DashScope API call failed", e);
            }
        }
    }

    // =========================================================================
    // StreamingChatModel (streaming)
    // =========================================================================

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        if (multimodal) {
            List<MultiModalMessage> messages = convertMultiModalMessages(chatRequest.messages());
            MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> builder =
                    buildMultiModalParam(messages, chatRequest, true);
            try {
                Flowable<MultiModalConversationResult> flowable = conversation.streamCall(builder.build());
                handleMultiModalStreaming(flowable, chatRequest, handler);
            } catch (Exception e) {
                handler.onError(new RuntimeException("DashScope streaming call failed", e));
            }
        } else {
            List<Message> messages = convertTextMessages(chatRequest.messages());
            GenerationParam.GenerationParamBuilder<?, ?> builder =
                    buildTextParam(messages, chatRequest, true);
            try {
                Flowable<GenerationResult> flowable = generation.streamCall(builder.build());
                handleTextStreaming(flowable, chatRequest, handler);
            } catch (Exception e) {
                handler.onError(new RuntimeException("DashScope streaming call failed", e));
            }
        }
    }

    // =========================================================================
    // Parameter building
    // =========================================================================

    // =========================================================================
    // Multimodal API parameter building (multimodal-generation endpoint)
    // =========================================================================

    private MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> buildMultiModalParam(
            List<MultiModalMessage> messages, ChatRequest chatRequest, boolean streaming) {

        MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> builder =
                MultiModalConversationParam.builder()
                        .apiKey(apiKey)
                        .model(model)
                        .messages(messages)
                        .enableSearch(enableSearch);

        if (streaming) {
            builder.incrementalOutput(true);
        }
        if (temperature != null) {
            builder.temperature(temperature.floatValue());
        }
        if (topP != null) {
            builder.topP(topP);
        }

        // Convert tool specifications from langchain4j to DashScope
        if (chatRequest.parameters() != null && chatRequest.parameters().toolSpecifications() != null) {
            List<ToolSpecification> toolSpecs = chatRequest.parameters().toolSpecifications();
            if (!toolSpecs.isEmpty()) {
                List<com.alibaba.dashscope.tools.ToolBase> tools = new ArrayList<>();
                for (ToolSpecification spec : toolSpecs) {
                    tools.add(convertToolSpecification(spec));
                }
                builder.tools(tools);
            }
        }

        return builder;
    }

    // =========================================================================
    // Text API parameter building (text-generation endpoint)
    // =========================================================================

    private GenerationParam.GenerationParamBuilder<?, ?> buildTextParam(
            List<Message> messages, ChatRequest chatRequest, boolean streaming) {

        GenerationParam.GenerationParamBuilder<?, ?> builder = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(messages)
                .enableSearch(enableSearch)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE);

        if (streaming) {
            builder.incrementalOutput(true);
        }
        if (temperature != null) {
            builder.temperature(temperature.floatValue());
        }
        if (topP != null) {
            builder.topP(topP);
        }

        // Convert tool specifications from langchain4j to DashScope
        if (chatRequest.parameters() != null && chatRequest.parameters().toolSpecifications() != null) {
            List<ToolSpecification> toolSpecs = chatRequest.parameters().toolSpecifications();
            if (!toolSpecs.isEmpty()) {
                List<com.alibaba.dashscope.tools.ToolBase> tools = new ArrayList<>();
                for (ToolSpecification spec : toolSpecs) {
                    tools.add(convertToolSpecification(spec));
                }
                builder.tools(tools);
            }
        }

        return builder;
    }

    // =========================================================================
    // Message conversion: langchain4j → DashScope
    // =========================================================================

    // =========================================================================
    // Message conversion: langchain4j → DashScope MultimodalMessage
    // =========================================================================

    private List<MultiModalMessage> convertMultiModalMessages(List<ChatMessage> messages) {
        List<MultiModalMessage> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg.type() == ChatMessageType.SYSTEM) {
                result.add(MultiModalMessage.builder()
                        .role(Role.SYSTEM.getValue())
                        .content(textContent(((SystemMessage) msg).text()))
                        .build());
            } else if (msg.type() == ChatMessageType.USER) {
                result.add(MultiModalMessage.builder()
                        .role(Role.USER.getValue())
                        .content(textContent(((UserMessage) msg).singleText()))
                        .build());
            } else if (msg.type() == ChatMessageType.AI) {
                AiMessage aiMsg = (AiMessage) msg;
                MultiModalMessage.MultiModalMessageBuilder<?, ?> msgBuilder =
                        MultiModalMessage.builder()
                                .role(Role.ASSISTANT.getValue())
                                .content(textContent(aiMsg.text() != null ? aiMsg.text() : ""));
                if (aiMsg.hasToolExecutionRequests()) {
                    List<ToolCallBase> toolCalls = new ArrayList<>();
                    for (ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                        ToolCallFunction tcf = new ToolCallFunction();
                        tcf.setId(req.id());
                        tcf.setType("function");
                        ToolCallFunction.CallFunction cf = tcf.new CallFunction();
                        cf.setName(req.name());
                        cf.setArguments(req.arguments());
                        tcf.setFunction(cf);
                        toolCalls.add(tcf);
                    }
                    msgBuilder.toolCalls(toolCalls);
                }
                result.add(msgBuilder.build());
            } else if (msg.type() == ChatMessageType.TOOL_EXECUTION_RESULT) {
                ToolExecutionResultMessage toolMsg = (ToolExecutionResultMessage) msg;
                result.add(MultiModalMessage.builder()
                        .role(Role.TOOL.getValue())
                        .content(textContent(toolMsg.text()))
                        .toolCallId(toolMsg.id())
                        .name(toolMsg.toolName())
                        .build());
            }
        }
        return result;
    }

    // =========================================================================
    // Message conversion: langchain4j → DashScope Message (text-only)
    // =========================================================================

    private List<Message> convertTextMessages(List<ChatMessage> messages) {
        List<Message> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg.type() == ChatMessageType.SYSTEM) {
                result.add(Message.builder()
                        .role(Role.SYSTEM.getValue())
                        .content(((SystemMessage) msg).text())
                        .build());
            } else if (msg.type() == ChatMessageType.USER) {
                result.add(Message.builder()
                        .role(Role.USER.getValue())
                        .content(((UserMessage) msg).singleText())
                        .build());
            } else if (msg.type() == ChatMessageType.AI) {
                AiMessage aiMsg = (AiMessage) msg;
                Message.MessageBuilder<?, ?> msgBuilder = Message.builder()
                        .role(Role.ASSISTANT.getValue())
                        .content(aiMsg.text() != null ? aiMsg.text() : "");
                if (aiMsg.hasToolExecutionRequests()) {
                    List<ToolCallBase> toolCalls = new ArrayList<>();
                    for (ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                        ToolCallFunction tcf = new ToolCallFunction();
                        tcf.setId(req.id());
                        tcf.setType("function");
                        ToolCallFunction.CallFunction cf = tcf.new CallFunction();
                        cf.setName(req.name());
                        cf.setArguments(req.arguments());
                        tcf.setFunction(cf);
                        toolCalls.add(tcf);
                    }
                    msgBuilder.toolCalls(toolCalls);
                }
                result.add(msgBuilder.build());
            } else if (msg.type() == ChatMessageType.TOOL_EXECUTION_RESULT) {
                ToolExecutionResultMessage toolMsg = (ToolExecutionResultMessage) msg;
                result.add(Message.builder()
                        .role(Role.TOOL.getValue())
                        .content(toolMsg.text())
                        .toolCallId(toolMsg.id())
                        .name(toolMsg.toolName())
                        .build());
            }
        }
        return result;
    }

    // =========================================================================
    // Tool specification conversion
    // =========================================================================

    private ToolFunction convertToolSpecification(ToolSpecification spec) {
        JsonObject params = new JsonObject();
        if (spec.parameters() != null) {
            // Serialize langchain4j JsonObjectSchema to JSON, then parse as Gson JsonObject
            String schemaJson = GSON.toJson(spec.parameters());
            params = GSON.fromJson(schemaJson, JsonObject.class);
        }
        FunctionDefinition fd = FunctionDefinition.builder()
                .name(spec.name())
                .description(spec.description())
                .parameters(params)
                .build();
        return ToolFunction.builder().function(fd).build();
    }

    // =========================================================================
    // Response conversion: DashScope → langchain4j
    // =========================================================================

    // =========================================================================
    // Response conversion: DashScope MultiModal → langchain4j
    // =========================================================================

    private ChatResponse convertMultiModalToChatResponse(MultiModalConversationResult result) {
        if (result.getOutput() == null || result.getOutput().getChoices() == null
                || result.getOutput().getChoices().isEmpty()) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(""))
                    .finishReason(FinishReason.STOP)
                    .build();
        }

        MultiModalConversationOutput.Choice choice = result.getOutput().getChoices().get(0);
        MultiModalMessage message = choice.getMessage();

        String text = extractText(message.getContent());
        List<ToolExecutionRequest> toolExecReqs = extractMultiModalToolRequests(message);

        AiMessage aiMessage = toolExecReqs.isEmpty()
                ? AiMessage.from(text)
                : AiMessage.from(text, toolExecReqs);

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .finishReason(mapFinishReason(choice.getFinishReason(), !toolExecReqs.isEmpty()))
                .build();
    }

    private List<ToolExecutionRequest> extractMultiModalToolRequests(MultiModalMessage message) {
        if (message.getToolCalls() == null || message.getToolCalls().isEmpty()) {
            return List.of();
        }
        List<ToolExecutionRequest> result = new ArrayList<>();
        for (ToolCallBase tcb : message.getToolCalls()) {
            if (tcb instanceof ToolCallFunction tcf) {
                String rawArgs = tcf.getFunction() != null && tcf.getFunction().getArguments() != null
                        ? tcf.getFunction().getArguments() : "";
                result.add(ToolExecutionRequest.builder()
                        .id(tcf.getId() != null ? tcf.getId() : "")
                        .name(tcf.getFunction() != null ? tcf.getFunction().getName() : "")
                        .arguments(normalizeToolArguments(rawArgs))
                        .build());
            }
        }
        return result;
    }

    // =========================================================================
    // Response conversion: DashScope Generation → langchain4j
    // =========================================================================

    private ChatResponse convertTextToChatResponse(GenerationResult result) {
        if (result.getOutput() == null || result.getOutput().getChoices() == null
                || result.getOutput().getChoices().isEmpty()) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(""))
                    .finishReason(FinishReason.STOP)
                    .build();
        }

        GenerationOutput.Choice choice = result.getOutput().getChoices().get(0);
        Message message = choice.getMessage();

        String text = message.getContent() != null ? message.getContent() : "";
        List<ToolExecutionRequest> toolExecReqs = extractTextToolRequests(message);

        AiMessage aiMessage = toolExecReqs.isEmpty()
                ? AiMessage.from(text)
                : AiMessage.from(text, toolExecReqs);

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .finishReason(mapFinishReason(choice.getFinishReason(), !toolExecReqs.isEmpty()))
                .build();
    }

    private List<ToolExecutionRequest> extractTextToolRequests(Message message) {
        if (message.getToolCalls() == null || message.getToolCalls().isEmpty()) {
            return List.of();
        }
        List<ToolExecutionRequest> result = new ArrayList<>();
        for (ToolCallBase tcb : message.getToolCalls()) {
            if (tcb instanceof ToolCallFunction tcf) {
                String rawArgs = tcf.getFunction() != null && tcf.getFunction().getArguments() != null
                        ? tcf.getFunction().getArguments() : "";
                result.add(ToolExecutionRequest.builder()
                        .id(tcf.getId() != null ? tcf.getId() : "")
                        .name(tcf.getFunction() != null ? tcf.getFunction().getName() : "")
                        .arguments(normalizeToolArguments(rawArgs))
                        .build());
            }
        }
        return result;
    }

    private FinishReason mapFinishReason(String dashScopeFinishReason, boolean hasToolCalls) {
        if (hasToolCalls || "tool_calls".equals(dashScopeFinishReason)) {
            return FinishReason.TOOL_EXECUTION;
        }
        if ("stop".equals(dashScopeFinishReason) || dashScopeFinishReason == null) {
            return FinishReason.STOP;
        }
        if ("length".equals(dashScopeFinishReason)) {
            return FinishReason.LENGTH;
        }
        return FinishReason.OTHER;
    }

    // =========================================================================
    // Streaming handler
    // =========================================================================

    // =========================================================================
    // Streaming handler: Multimodal
    // =========================================================================

    private void handleMultiModalStreaming(Flowable<MultiModalConversationResult> flowable,
                                           ChatRequest chatRequest,
                                           StreamingChatResponseHandler handler) {
        // Accumulator for tool call chunks in incremental output mode
        Map<Integer, AccumulatedToolCall> toolCallAccumulator = new HashMap<>();
        boolean hasToolCalls = false;
        MultiModalConversationResult lastResult = null;
        String lastFinishReason = null;

        try {
            for (MultiModalConversationResult result : flowable.blockingIterable()) {
                if (result.getOutput() == null || result.getOutput().getChoices() == null
                        || result.getOutput().getChoices().isEmpty()) {
                    continue;
                }

                MultiModalConversationOutput.Choice choice = result.getOutput().getChoices().get(0);
                MultiModalMessage msg = choice.getMessage();
                lastResult = result;

                String fr = choice.getFinishReason();
                if (fr != null && !fr.isEmpty()) {
                    lastFinishReason = fr;
                }

                // Handle tool call chunks — accumulate them
                if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    hasToolCalls = true;
                    for (ToolCallBase tcb : msg.getToolCalls()) {
                        if (tcb instanceof ToolCallFunction tcf) {
                            int idx = tcf.getIndex() != null ? tcf.getIndex() : 0;
                            toolCallAccumulator.computeIfAbsent(idx, k -> new AccumulatedToolCall())
                                    .merge(tcf);
                        }
                    }
                    continue;
                }

                // Handle reasoning/thinking content
                String reasoning = msg.getReasoningContent();
                if (reasoning != null && !reasoning.isEmpty()) {
                    // Route thinking content to all registered sinks
                    for (Sinks.Many<String> sink : THINKING_SINKS.values()) {
                        sink.tryEmitNext(reasoning);
                    }
                    handler.onPartialThinking(new PartialThinking(reasoning));
                }

                // If stream has tool calls, suppress text emission
                if (hasToolCalls) {
                    continue;
                }

                // Text-only streaming: emit tokens immediately
                String content = extractText(msg.getContent());
                if (!content.isEmpty()) {
                    // Route text content to all registered sinks for true streaming
                    for (Sinks.Many<String> sink : TEXT_SINKS.values()) {
                        sink.tryEmitNext(content);
                    }
                    handler.onPartialResponse(content);
                }
            }

            // Build and emit final complete response
            if (hasToolCalls && !toolCallAccumulator.isEmpty()) {
                List<ToolExecutionRequest> finalToolCalls = buildToolCallsFromAccumulator(toolCallAccumulator);
                AiMessage aiMessage = AiMessage.from("", finalToolCalls);
                FinishReason finishReason = mapFinishReason(lastFinishReason, true);
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(aiMessage)
                        .finishReason(finishReason)
                        .build());
            } else {
                // Text-only stream: emit final marker
                AiMessage aiMessage = AiMessage.from("");
                FinishReason finishReason = mapFinishReason(lastFinishReason, false);
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(aiMessage)
                        .finishReason(finishReason)
                        .build());
            }
        } catch (Exception e) {
            handler.onError(e);
            return;
        }
    }

    // =========================================================================
    // Streaming handler: Text Generation
    // =========================================================================

    private void handleTextStreaming(Flowable<GenerationResult> flowable,
                                     ChatRequest chatRequest,
                                     StreamingChatResponseHandler handler) {
        Map<Integer, AccumulatedToolCall> toolCallAccumulator = new HashMap<>();
        boolean hasToolCalls = false;
        GenerationResult lastResult = null;
        String lastFinishReason = null;

        try {
            for (GenerationResult result : flowable.blockingIterable()) {
                if (result.getOutput() == null || result.getOutput().getChoices() == null
                        || result.getOutput().getChoices().isEmpty()) {
                    continue;
                }

                GenerationOutput.Choice choice = result.getOutput().getChoices().get(0);
                Message msg = choice.getMessage();
                lastResult = result;

                String fr = choice.getFinishReason();
                if (fr != null && !fr.isEmpty()) {
                    lastFinishReason = fr;
                }

                // Handle tool call chunks — accumulate them
                if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    hasToolCalls = true;
                    for (ToolCallBase tcb : msg.getToolCalls()) {
                        if (tcb instanceof ToolCallFunction tcf) {
                            int idx = tcf.getIndex() != null ? tcf.getIndex() : 0;
                            toolCallAccumulator.computeIfAbsent(idx, k -> new AccumulatedToolCall())
                                    .merge(tcf);
                        }
                    }
                    continue;
                }

                // Handle reasoning/thinking content
                String reasoning = msg.getReasoningContent();
                if (reasoning != null && !reasoning.isEmpty()) {
                    for (Sinks.Many<String> sink : THINKING_SINKS.values()) {
                        sink.tryEmitNext(reasoning);
                    }
                    handler.onPartialThinking(new PartialThinking(reasoning));
                }

                // If stream has tool calls, suppress text emission
                if (hasToolCalls) {
                    continue;
                }

                // Text-only streaming: emit tokens immediately
                String content = msg.getContent();
                if (content != null && !content.isEmpty()) {
                    for (Sinks.Many<String> sink : TEXT_SINKS.values()) {
                        sink.tryEmitNext(content);
                    }
                    handler.onPartialResponse(content);
                }
            }

            // Build and emit final complete response
            if (hasToolCalls && !toolCallAccumulator.isEmpty()) {
                List<ToolExecutionRequest> finalToolCalls = buildToolCallsFromAccumulator(toolCallAccumulator);
                AiMessage aiMessage = AiMessage.from("", finalToolCalls);
                FinishReason finishReason = mapFinishReason(lastFinishReason, true);
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(aiMessage)
                        .finishReason(finishReason)
                        .build());
            } else {
                AiMessage aiMessage = AiMessage.from("");
                FinishReason finishReason = mapFinishReason(lastFinishReason, false);
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(aiMessage)
                        .finishReason(finishReason)
                        .build());
            }
        } catch (Exception e) {
            handler.onError(e);
            return;
        }
    }

    private List<ToolExecutionRequest> buildToolCallsFromAccumulator(
            Map<Integer, AccumulatedToolCall> accumulator) {
        return accumulator.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> ToolExecutionRequest.builder()
                        .id(entry.getValue().id != null ? entry.getValue().id : "")
                        .name(entry.getValue().name != null ? entry.getValue().name : "")
                        .arguments(normalizeToolArguments(entry.getValue().arguments.toString()))
                        .build())
                .toList();
    }

    // =========================================================================
    // Utility methods
    // =========================================================================

    private static List<Map<String, Object>> textContent(String text) {
        return Collections.singletonList(Collections.singletonMap("text", text != null ? text : ""));
    }

    private static String extractText(List<Map<String, Object>> content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> item : content) {
            Object text = item.get("text");
            if (text != null) {
                sb.append(text);
            }
        }
        return sb.toString();
    }

    /**
     * Normalizes tool call arguments by converting string values that are JSON objects/arrays
     * into their proper parsed form. DashScope models sometimes return Map-type parameters
     * (e.g. headers, queryParams) as JSON-encoded strings like "{}" instead of actual JSON
     * objects, which causes langchain4j's DefaultToolExecutor to fail during deserialization.
     */
    private String normalizeToolArguments(String rawArgs) {
        if (rawArgs == null || rawArgs.isBlank()) {
            return "{}";
        }
        try {
            JsonElement parsed = JsonParser.parseString(rawArgs);
            if (parsed.isJsonObject()) {
                normalizeJsonValues(parsed.getAsJsonObject());
                return GSON.toJson(parsed);
            }
        } catch (Exception e) {
            log.debug("Failed to normalize tool参数: {}", rawArgs);
        }
        return rawArgs;
    }

    /**
     * Recursively walks a JSON object and converts string values that represent
     * JSON objects or arrays into their parsed equivalents.
     */
    private void normalizeJsonValues(JsonObject obj) {
        for (var key : new ArrayList<>(obj.keySet())) {
            JsonElement value = obj.get(key);
            if (value.isJsonPrimitive()) {
                JsonPrimitive prim = value.getAsJsonPrimitive();
                if (prim.isString()) {
                    String str = prim.getAsString();
                    String trimmed = str.trim();
                    if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                            || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                        try {
                            obj.add(key, JsonParser.parseString(trimmed));
                        } catch (Exception e) {
                            // Not valid JSON — keep as string
                        }
                    }
                }
            } else if (value.isJsonObject()) {
                normalizeJsonValues(value.getAsJsonObject());
            } else if (value.isJsonArray()) {
                normalizeJsonArrayValues(value.getAsJsonArray());
            }
        }
    }

    private void normalizeJsonArrayValues(JsonArray arr) {
        for (int i = 0; i < arr.size(); i++) {
            JsonElement elem = arr.get(i);
            if (elem.isJsonObject()) {
                normalizeJsonValues(elem.getAsJsonObject());
            } else if (elem.isJsonArray()) {
                normalizeJsonArrayValues(elem.getAsJsonArray());
            }
        }
    }

    /**
     * Accumulator for merging tool call chunks from the streaming response.
     * In DashScope incremental output mode, tool call arguments arrive as fragments.
     */
    private static class AccumulatedToolCall {
        String id;
        String name;
        StringBuilder arguments = new StringBuilder();

        void merge(ToolCallFunction tcf) {
            if (tcf.getId() != null && !tcf.getId().isEmpty()) {
                this.id = tcf.getId();
            }
            if (tcf.getFunction() != null) {
                if (tcf.getFunction().getName() != null && !tcf.getFunction().getName().isEmpty()) {
                    this.name = tcf.getFunction().getName();
                }
                if (tcf.getFunction().getArguments() != null) {
                    this.arguments.append(tcf.getFunction().getArguments());
                }
            }
        }
    }
}

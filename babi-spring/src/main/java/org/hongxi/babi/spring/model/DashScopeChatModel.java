package org.hongxi.babi.spring.model;

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
import com.google.gson.JsonObject;
import io.reactivex.Flowable;
import org.hongxi.babi.common.model.ModelCatalog;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DashScope Chat Model implementation that adapts the DashScope Java SDK
 * to Spring AI's {@link ChatModel} interface.
 *
 * <p>Automatically routes to the appropriate DashScope API based on model type:
 * multimodal models use {@link MultiModalConversation} API, while text-only models
 * use {@link Generation} API. Model detection is handled by
 * {@link org.hongxi.babi.common.model.ModelCatalog#isMultimodalModel(String)}.
 *
 * <p>Supports both synchronous and streaming modes with proper tool call handling.
 * For streaming, tool call chunks are accumulated and merged before emitting,
 * ensuring that Spring AI's {@code MessageAggregator} receives complete tool calls.
 */
public class DashScopeChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(DashScopeChatModel.class);

    private final String apiKey;
    private final String defaultModel;
    private final Double temperature;
    private final Double topP;
    private final boolean enableSearch;
    // Both SDK clients pre-created (stateless lightweight objects); routing per-request
    private final MultiModalConversation conversation = new MultiModalConversation();
    private final Generation generation = new Generation();

    public DashScopeChatModel(
            String apiKey,
            String defaultModel,
            Double temperature,
            Double topP,
            boolean enableSearch) {
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.temperature = temperature;
        this.topP = topP;
        this.enableSearch = enableSearch;
        log.info("DashScope model '{}' (default), per-request override enabled", defaultModel);
    }

    /**
     * Resolve the model name for this request: use the override from ChatOptions
     * if present, otherwise fall back to the default model.
     */
    private String resolveModel(ChatOptions options) {
        if (options != null && options.getModel() != null && !options.getModel().isBlank()) {
            return options.getModel();
        }
        return defaultModel;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String model = resolveModel(prompt.getOptions());
        if (ModelCatalog.isMultimodalModel(model)) {
            List<MultiModalMessage> messages = convertMultiModalMessages(prompt.getInstructions());
            MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> builder =
                    buildMultiModalParam(messages, prompt, false);
            try {
                MultiModalConversationResult result = conversation.call(builder.build());
                return convertMultiModalToChatResponse(result);
            } catch (Exception e) {
                throw new RuntimeException("DashScope API call failed", e);
            }
        } else {
            List<Message> messages = convertTextMessages(prompt.getInstructions());
            GenerationParam.GenerationParamBuilder<?, ?> builder =
                    buildTextParam(messages, prompt, false);
            try {
                GenerationResult result = generation.call(builder.build());
                return convertTextToChatResponse(result);
            } catch (Exception e) {
                throw new RuntimeException("DashScope API call failed", e);
            }
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        String model = resolveModel(prompt.getOptions());
        if (ModelCatalog.isMultimodalModel(model)) {
            List<MultiModalMessage> messages = convertMultiModalMessages(prompt.getInstructions());
            MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> builder =
                    buildMultiModalParam(messages, prompt, true);
            try {
                Flowable<MultiModalConversationResult> flowable = conversation.streamCall(builder.build());
                return convertMultiModalToFlux(flowable);
            } catch (Exception e) {
                return Flux.error(new RuntimeException("DashScope API stream call failed", e));
            }
        } else {
            List<Message> messages = convertTextMessages(prompt.getInstructions());
            GenerationParam.GenerationParamBuilder<?, ?> builder =
                    buildTextParam(messages, prompt, true);
            try {
                Flowable<GenerationResult> flowable = generation.streamCall(builder.build());
                return convertTextToFlux(flowable);
            } catch (Exception e) {
                return Flux.error(new RuntimeException("DashScope API stream call failed", e));
            }
        }
    }

    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder()
                .model(defaultModel)
                .temperature(temperature)
                .topP(topP)
                .build();
    }

    // -------------------------------------------------------------------------
    // Internal conversion methods
    // -------------------------------------------------------------------------

    /**
     * Apply Spring AI chat options to the text param builder.
     */
    private void applyTextOptions(GenerationParam.GenerationParamBuilder<?, ?> builder, ChatOptions options) {
        if (options instanceof ToolCallingChatOptions toolOptions && toolOptions.getToolCallbacks() != null
                && !toolOptions.getToolCallbacks().isEmpty()) {
            List<com.alibaba.dashscope.tools.ToolBase> tools = new ArrayList<>();
            toolOptions.getToolCallbacks().forEach(cb -> {
                String schemaJson = cb.getToolDefinition().inputSchema();
                JsonObject params = new JsonObject();
                if (schemaJson != null && !schemaJson.isEmpty()) {
                    params = new com.google.gson.Gson().fromJson(schemaJson, JsonObject.class);
                }
                FunctionDefinition fd = FunctionDefinition.builder()
                        .name(cb.getToolDefinition().name())
                        .description(cb.getToolDefinition().description())
                        .parameters(params)
                        .build();
                tools.add(ToolFunction.builder().function(fd).build());
            });
            builder.tools(tools);
        }
        if (options != null) {
            if (options.getTemperature() != null) {
                builder.temperature(options.getTemperature().floatValue());
            }
            if (options.getTopP() != null) {
                builder.topP(options.getTopP());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Message conversion: Spring AI → DashScope MultiModalMessage
    // -------------------------------------------------------------------------

    /**
     * Convert Spring AI messages to DashScope MultiModalMessages.
     *
     * <p>For MultiModalMessage, text content is represented as a list of maps:
     * {@code [{"text": "actual content"}]}.
     */
    private List<MultiModalMessage> convertMultiModalMessages(List<org.springframework.ai.chat.messages.Message> springMessages) {
        List<MultiModalMessage> result = new ArrayList<>();
        for (org.springframework.ai.chat.messages.Message springMsg : springMessages) {
            if (springMsg instanceof SystemMessage sysMsg) {
                result.add(MultiModalMessage.builder()
                        .role(Role.SYSTEM.getValue())
                        .content(textContent(sysMsg.getText()))
                        .build());
            } else if (springMsg instanceof UserMessage userMsg) {
                result.add(MultiModalMessage.builder()
                        .role(Role.USER.getValue())
                        .content(textContent(userMsg.getText()))
                        .build());
            } else if (springMsg instanceof AssistantMessage assistantMsg) {
                MultiModalMessage.MultiModalMessageBuilder<?, ?> msgBuilder = MultiModalMessage.builder()
                        .role(Role.ASSISTANT.getValue())
                        .content(textContent(assistantMsg.getText()));
                if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                    List<ToolCallBase> toolCalls = new ArrayList<>();
                    for (AssistantMessage.ToolCall tc : assistantMsg.getToolCalls()) {
                        ToolCallFunction tcf = new ToolCallFunction();
                        tcf.setId(tc.id());
                        tcf.setType("function");
                        ToolCallFunction.CallFunction cf = tcf.new CallFunction();
                        cf.setName(tc.name());
                        cf.setArguments(tc.arguments());
                        tcf.setFunction(cf);
                        toolCalls.add(tcf);
                    }
                    msgBuilder.toolCalls(toolCalls);
                }
                result.add(msgBuilder.build());
            } else if (springMsg instanceof ToolResponseMessage toolResponseMsg) {
                for (ToolResponseMessage.ToolResponse tr : toolResponseMsg.getResponses()) {
                    result.add(MultiModalMessage.builder()
                            .role(Role.TOOL.getValue())
                            .content(textContent(tr.responseData()))
                            .toolCallId(tr.id())
                            .name(tr.name())
                            .build());
                }
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Message conversion: Spring AI → DashScope Message (text-only)
    // -------------------------------------------------------------------------

    private List<Message> convertTextMessages(List<org.springframework.ai.chat.messages.Message> springMessages) {
        List<Message> result = new ArrayList<>();
        for (org.springframework.ai.chat.messages.Message springMsg : springMessages) {
            if (springMsg instanceof SystemMessage sysMsg) {
                result.add(Message.builder()
                        .role(Role.SYSTEM.getValue())
                        .content(sysMsg.getText())
                        .build());
            } else if (springMsg instanceof UserMessage userMsg) {
                result.add(Message.builder()
                        .role(Role.USER.getValue())
                        .content(userMsg.getText())
                        .build());
            } else if (springMsg instanceof AssistantMessage assistantMsg) {
                Message.MessageBuilder<?, ?> msgBuilder = Message.builder()
                        .role(Role.ASSISTANT.getValue())
                        .content(assistantMsg.getText());
                if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                    List<ToolCallBase> toolCalls = new ArrayList<>();
                    for (AssistantMessage.ToolCall tc : assistantMsg.getToolCalls()) {
                        ToolCallFunction tcf = new ToolCallFunction();
                        tcf.setId(tc.id());
                        tcf.setType("function");
                        ToolCallFunction.CallFunction cf = tcf.new CallFunction();
                        cf.setName(tc.name());
                        cf.setArguments(tc.arguments());
                        tcf.setFunction(cf);
                        toolCalls.add(tcf);
                    }
                    msgBuilder.toolCalls(toolCalls);
                }
                result.add(msgBuilder.build());
            } else if (springMsg instanceof ToolResponseMessage toolResponseMsg) {
                for (ToolResponseMessage.ToolResponse tr : toolResponseMsg.getResponses()) {
                    result.add(Message.builder()
                            .role(Role.TOOL.getValue())
                            .content(tr.responseData())
                            .toolCallId(tr.id())
                            .name(tr.name())
                            .build());
                }
            }
        }
        return result;
    }

    /**
     * Wrap a text string into the MultiModalMessage content format:
     * {@code [{"text": text}]}.
     */
    private static List<Map<String, Object>> textContent(String text) {
        return Collections.singletonList(Collections.singletonMap("text", text != null ? text : ""));
    }

    /**
     * Extract plain text from a MultiModalMessage content list.
     * The content is a list of maps like {@code [{"text": "actual content"}]}.
     */
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

    // -------------------------------------------------------------------------
    // Multimodal API parameter building
    // -------------------------------------------------------------------------

    private MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> buildMultiModalParam(
            List<MultiModalMessage> messages, Prompt prompt, boolean streaming) {

        String model = resolveModel(prompt.getOptions());
        MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> builder =
                MultiModalConversationParam.builder()
                        .apiKey(apiKey)
                        .model(model)
                        .messages(messages)
                        .enableSearch(enableSearch);

        if (streaming) {
            builder.incrementalOutput(true);
        }
        applyMultiModalOptions(builder, prompt.getOptions());
        return builder;
    }

    // -------------------------------------------------------------------------
    // Text API parameter building
    // -------------------------------------------------------------------------

    private GenerationParam.GenerationParamBuilder<?, ?> buildTextParam(
            List<Message> messages, Prompt prompt, boolean streaming) {

        String model = resolveModel(prompt.getOptions());
        GenerationParam.GenerationParamBuilder<?, ?> builder = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(messages)
                .enableSearch(enableSearch)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE);

        if (streaming) {
            builder.incrementalOutput(true);
        }
        applyTextOptions(builder, prompt.getOptions());
        return builder;
    }

    /**
     * Apply Spring AI chat options to the multimodal param builder.
     */
    private void applyMultiModalOptions(MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> builder, ChatOptions options) {
        if (options instanceof ToolCallingChatOptions toolOptions && toolOptions.getToolCallbacks() != null
                && !toolOptions.getToolCallbacks().isEmpty()) {
            List<com.alibaba.dashscope.tools.ToolBase> tools = new ArrayList<>();
            toolOptions.getToolCallbacks().forEach(cb -> {
                String schemaJson = cb.getToolDefinition().inputSchema();
                JsonObject params = new JsonObject();
                if (schemaJson != null && !schemaJson.isEmpty()) {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    params = gson.fromJson(schemaJson, JsonObject.class);
                }
                FunctionDefinition fd = FunctionDefinition.builder()
                        .name(cb.getToolDefinition().name())
                        .description(cb.getToolDefinition().description())
                        .parameters(params)
                        .build();
                tools.add(ToolFunction.builder().function(fd).build());
            });
            builder.tools(tools);
        }
        if (options != null) {
            if (options.getTemperature() != null) {
                builder.temperature(options.getTemperature().floatValue());
            }
            if (options.getTopP() != null) {
                builder.topP(options.getTopP());
            }
        }
    }

    /**
     * Convert a DashScope MultiModalConversationResult to a Spring AI ChatResponse.
     */
    // -------------------------------------------------------------------------
    // Response conversion: DashScope MultiModal → Spring AI
    // -------------------------------------------------------------------------

    private ChatResponse convertMultiModalToChatResponse(MultiModalConversationResult result) {
        if (result.getOutput() == null || result.getOutput().getChoices() == null
                || result.getOutput().getChoices().isEmpty()) {
            return new ChatResponse(List.of());
        }

        MultiModalConversationOutput.Choice choice = result.getOutput().getChoices().get(0);
        MultiModalMessage message = choice.getMessage();

        List<AssistantMessage.ToolCall> toolCalls = extractMultiModalToolCalls(message);

        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(extractText(message.getContent()))
                .toolCalls(toolCalls)
                .build();

        ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
                .finishReason(choice.getFinishReason())
                .build();

        ChatResponseMetadata responseMetadata = buildMultiModalResponseMetadata(result);

        return new ChatResponse(
                List.of(new org.springframework.ai.chat.model.Generation(assistantMessage, metadata)),
                responseMetadata);
    }

    private List<AssistantMessage.ToolCall> extractMultiModalToolCalls(MultiModalMessage message) {
        if (message.getToolCalls() == null || message.getToolCalls().isEmpty()) {
            return List.of();
        }
        List<AssistantMessage.ToolCall> result = new ArrayList<>();
        for (ToolCallBase tcb : message.getToolCalls()) {
            if (tcb instanceof ToolCallFunction tcf) {
                result.add(new AssistantMessage.ToolCall(
                        tcf.getId() != null ? tcf.getId() : "",
                        "function",
                        tcf.getFunction() != null ? tcf.getFunction().getName() : "",
                        tcf.getFunction() != null && tcf.getFunction().getArguments() != null
                                ? tcf.getFunction().getArguments() : ""));
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Streaming conversion: DashScope MultiModal → Flux
    // -------------------------------------------------------------------------

    /**
     * Convert a DashScope MultiModal Flowable stream to a Reactor Flux.
     *
     * <p>Text content deltas are emitted immediately for true streaming.
     * Tool call chunks are accumulated internally and emitted as a single
     * complete response at the end of the stream (when finish_reason arrives).
     *
     * <p>If the stream contains tool calls, text deltas are suppressed and only
     * the final aggregated response (with tool calls) is emitted, so that Spring
     * AI's MessageAggregator can properly detect and execute tool calls.
     */
    private Flux<ChatResponse> convertMultiModalToFlux(Flowable<MultiModalConversationResult> flowable) {
        return Flux.<ChatResponse>create(sink -> {
            // Accumulator for tool call chunks across the stream
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

                    // Track finish reason (may be null, empty, or meaningful like "stop"/"tool_calls")
                    String fr = choice.getFinishReason();
                    if (fr != null && !fr.isEmpty()) {
                        lastFinishReason = fr;
                    }

                    // Detect tool call chunks — accumulate them
                    if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                        hasToolCalls = true;
                        for (ToolCallBase tcb : msg.getToolCalls()) {
                            if (tcb instanceof ToolCallFunction tcf) {
                                int idx = tcf.getIndex() != null ? tcf.getIndex() : 0;
                                toolCallAccumulator.computeIfAbsent(idx, k -> new AccumulatedToolCall())
                                        .merge(tcf);
                            }
                        }
                        // Don't emit here — wait until stream ends to ensure all fragments are accumulated
                        continue;
                    }

                    // Extract reasoning content if present
                    String reasoning = msg.getReasoningContent();
                    if (reasoning != null && !reasoning.isEmpty()) {
                        AssistantMessage reasoningMsg = AssistantMessage.builder()
                                .content("")
                                .properties(Map.of("reasoningContent", reasoning))
                                .build();
                        sink.next(new ChatResponse(
                                List.of(new org.springframework.ai.chat.model.Generation(reasoningMsg))));
                    }

                    // If this chunk has tool calls, skip text emission
                    if (hasToolCalls) {
                        continue;
                    }

                    // Text-only stream: emit each delta immediately for true streaming
                    String content = extractText(msg.getContent());
                    if (!content.isEmpty()) {
                        AssistantMessage assistantMessage = AssistantMessage.builder()
                                .content(content)
                                .build();
                        sink.next(new ChatResponse(
                                List.of(new org.springframework.ai.chat.model.Generation(
                                        assistantMessage))));
                    }
                }

                // After stream ends, emit final result
                if (hasToolCalls && !toolCallAccumulator.isEmpty()) {
                    List<AssistantMessage.ToolCall> finalToolCalls =
                            buildToolCallsFromAccumulator(toolCallAccumulator);

                    AssistantMessage assistantMessage = AssistantMessage.builder()
                            .content("")
                            .toolCalls(finalToolCalls)
                            .build();

                    ChatGenerationMetadata.Builder metaBuilder = ChatGenerationMetadata.builder();
                    if (lastFinishReason != null) {
                        metaBuilder.finishReason(lastFinishReason);
                    }

                    ChatResponseMetadata responseMetadata = (lastResult != null)
                            ? buildMultiModalResponseMetadata(lastResult)
                            : ChatResponseMetadata.builder().build();

                    sink.next(new ChatResponse(
                            List.of(new org.springframework.ai.chat.model.Generation(
                                    assistantMessage, metaBuilder.build())),
                            responseMetadata));
                } else if (!hasToolCalls && lastFinishReason != null) {
                    // Text-only stream: emit finish marker with finish reason
                    AssistantMessage assistantMessage = AssistantMessage.builder()
                            .content("")
                            .build();
                    ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
                            .finishReason(lastFinishReason)
                            .build();
                    ChatResponseMetadata responseMetadata = (lastResult != null)
                            ? buildMultiModalResponseMetadata(lastResult)
                            : ChatResponseMetadata.builder().build();
                    sink.next(new ChatResponse(
                            List.of(new org.springframework.ai.chat.model.Generation(
                                    assistantMessage, metadata)),
                            responseMetadata));
                }
            } catch (Exception e) {
                sink.error(e);
                return;
            }
            sink.complete();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Extract tool calls from a DashScope MultiModalMessage.
     */
    // -------------------------------------------------------------------------
    // Response conversion: DashScope Generation → Spring AI
    // -------------------------------------------------------------------------

    private ChatResponse convertTextToChatResponse(GenerationResult result) {
        if (result.getOutput() == null || result.getOutput().getChoices() == null
                || result.getOutput().getChoices().isEmpty()) {
            return new ChatResponse(List.of());
        }

        GenerationOutput.Choice choice = result.getOutput().getChoices().get(0);
        Message message = choice.getMessage();

        List<AssistantMessage.ToolCall> toolCalls = extractTextToolCalls(message);

        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(message.getContent() != null ? message.getContent() : "")
                .toolCalls(toolCalls)
                .build();

        ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
                .finishReason(choice.getFinishReason())
                .build();

        ChatResponseMetadata responseMetadata = buildTextResponseMetadata(result);

        return new ChatResponse(
                List.of(new org.springframework.ai.chat.model.Generation(assistantMessage, metadata)),
                responseMetadata);
    }

    private List<AssistantMessage.ToolCall> extractTextToolCalls(Message message) {
        if (message.getToolCalls() == null || message.getToolCalls().isEmpty()) {
            return List.of();
        }
        List<AssistantMessage.ToolCall> result = new ArrayList<>();
        for (ToolCallBase tcb : message.getToolCalls()) {
            if (tcb instanceof ToolCallFunction tcf) {
                result.add(new AssistantMessage.ToolCall(
                        tcf.getId() != null ? tcf.getId() : "",
                        "function",
                        tcf.getFunction() != null ? tcf.getFunction().getName() : "",
                        tcf.getFunction() != null && tcf.getFunction().getArguments() != null
                                ? tcf.getFunction().getArguments() : ""));
            }
        }
        return result;
    }

    /**
     * Build merged tool calls from the accumulator.
     */
    private List<AssistantMessage.ToolCall> buildToolCallsFromAccumulator(
            Map<Integer, AccumulatedToolCall> accumulator) {
        return accumulator.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new AssistantMessage.ToolCall(
                        entry.getValue().id != null ? entry.getValue().id : "",
                        "function",
                        entry.getValue().name != null ? entry.getValue().name : "",
                        entry.getValue().arguments.toString()))
                .toList();
    }

    /**
     * Build ChatResponseMetadata from a DashScope MultiModalConversationResult.
     */
    // -------------------------------------------------------------------------
    // Streaming conversion: DashScope Generation → Flux
    // -------------------------------------------------------------------------

    private Flux<ChatResponse> convertTextToFlux(Flowable<GenerationResult> flowable) {
        return Flux.<ChatResponse>create(sink -> {
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

                    // Detect tool call chunks — accumulate them
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

                    // Extract reasoning content if present
                    String reasoning = msg.getReasoningContent();
                    if (reasoning != null && !reasoning.isEmpty()) {
                        AssistantMessage reasoningMsg = AssistantMessage.builder()
                                .content("")
                                .properties(Map.of("reasoningContent", reasoning))
                                .build();
                        sink.next(new ChatResponse(
                                List.of(new org.springframework.ai.chat.model.Generation(reasoningMsg))));
                    }

                    // If this chunk has tool calls, skip text emission
                    if (hasToolCalls) {
                        continue;
                    }

                    // Text-only stream: emit each delta immediately for true streaming
                    String content = msg.getContent();
                    if (content != null && !content.isEmpty()) {
                        AssistantMessage assistantMessage = AssistantMessage.builder()
                                .content(content)
                                .build();
                        sink.next(new ChatResponse(
                                List.of(new org.springframework.ai.chat.model.Generation(
                                        assistantMessage))));
                    }
                }

                // After stream ends, emit final result
                if (hasToolCalls && !toolCallAccumulator.isEmpty()) {
                    List<AssistantMessage.ToolCall> finalToolCalls =
                            buildToolCallsFromAccumulator(toolCallAccumulator);

                    AssistantMessage assistantMessage = AssistantMessage.builder()
                            .content("")
                            .toolCalls(finalToolCalls)
                            .build();

                    ChatGenerationMetadata.Builder metaBuilder = ChatGenerationMetadata.builder();
                    if (lastFinishReason != null) {
                        metaBuilder.finishReason(lastFinishReason);
                    }

                    ChatResponseMetadata responseMetadata = (lastResult != null)
                            ? buildTextResponseMetadata(lastResult)
                            : ChatResponseMetadata.builder().build();

                    sink.next(new ChatResponse(
                            List.of(new org.springframework.ai.chat.model.Generation(
                                    assistantMessage, metaBuilder.build())),
                            responseMetadata));
                } else if (!hasToolCalls && lastFinishReason != null) {
                    AssistantMessage assistantMessage = AssistantMessage.builder()
                            .content("")
                            .build();
                    ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
                            .finishReason(lastFinishReason)
                            .build();
                    ChatResponseMetadata responseMetadata = (lastResult != null)
                            ? buildTextResponseMetadata(lastResult)
                            : ChatResponseMetadata.builder().build();
                    sink.next(new ChatResponse(
                            List.of(new org.springframework.ai.chat.model.Generation(
                                    assistantMessage, metadata)),
                            responseMetadata));
                }
            } catch (Exception e) {
                sink.error(e);
                return;
            }
            sink.complete();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // -------------------------------------------------------------------------
    // Response metadata builders
    // -------------------------------------------------------------------------

    private ChatResponseMetadata buildMultiModalResponseMetadata(MultiModalConversationResult result) {
        ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder()
                .id(result.getRequestId() != null ? result.getRequestId() : "");
        if (result.getUsage() != null) {
            builder.usage(new DashScopeUsage(
                    result.getUsage().getInputTokens(),
                    result.getUsage().getOutputTokens(),
                    result.getUsage().getTotalTokens()));
        }
        return builder.build();
    }

    private ChatResponseMetadata buildTextResponseMetadata(GenerationResult result) {
        ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder()
                .id(result.getRequestId() != null ? result.getRequestId() : "");
        if (result.getUsage() != null) {
            builder.usage(new DashScopeUsage(
                    result.getUsage().getInputTokens(),
                    result.getUsage().getOutputTokens(),
                    result.getUsage().getTotalTokens()));
        }
        return builder.build();
    }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    /**
     * Accumulator for merging tool call chunks from the streaming response.
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

    /**
     * Usage implementation for DashScope response.
     */
    private record DashScopeUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens)
            implements org.springframework.ai.chat.metadata.Usage {

        @Override
        public Integer getPromptTokens() {
            return promptTokens != null ? promptTokens : 0;
        }

        @Override
        public Integer getCompletionTokens() {
            return completionTokens != null ? completionTokens : 0;
        }

        @Override
        public Integer getTotalTokens() {
            return totalTokens != null ? totalTokens : 0;
        }

        @Override
        public Map<String, Integer> getNativeUsage() {
            Map<String, Integer> usage = new HashMap<>();
            usage.put("promptTokens", getPromptTokens());
            usage.put("completionTokens", getCompletionTokens());
            usage.put("totalTokens", getTotalTokens());
            return usage;
        }
    }
}

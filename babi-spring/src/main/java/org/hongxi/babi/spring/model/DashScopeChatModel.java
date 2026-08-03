package org.hongxi.babi.spring.model;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolCallBase;
import com.alibaba.dashscope.tools.ToolCallFunction;
import com.alibaba.dashscope.tools.ToolFunction;
import com.google.gson.JsonObject;
import io.reactivex.Flowable;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DashScope Chat Model implementation that adapts the DashScope Java SDK
 * to Spring AI's {@link ChatModel} interface.
 *
 * <p>This implementation uses the DashScope SDK's native API directly,
 * bypassing the OpenAI-compatible endpoint which has bugs in streaming mode
 * with tool calls (function field is null in chunks causing NoSuchElementException).
 *
 * <p>Supports both synchronous and streaming modes with proper tool call handling.
 * For streaming, tool call chunks are accumulated and merged before emitting,
 * ensuring that Spring AI's {@code MessageAggregator} receives complete tool calls.
 */
public class DashScopeChatModel implements ChatModel {

    private final String apiKey;
    private final String model;
    private final Double temperature;
    private final Double topP;
    private final boolean enableSearch;
    private final Generation generation;

    public DashScopeChatModel(
            String apiKey,
            String model,
            Double temperature,
            Double topP,
            boolean enableSearch) {
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.topP = topP;
        this.enableSearch = enableSearch;
        this.generation = new Generation();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<Message> dashScopeMessages = convertMessages(prompt.getInstructions());
        GenerationParam.GenerationParamBuilder<?, ?> builder = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(dashScopeMessages)
                .enableSearch(enableSearch)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE);

        applyOptions(builder, prompt.getOptions());

        try {
            GenerationResult result = generation.call(builder.build());
            return convertToChatResponse(result);
        } catch (Exception e) {
            throw new RuntimeException("DashScope API call failed", e);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        List<Message> dashScopeMessages = convertMessages(prompt.getInstructions());
        GenerationParam.GenerationParamBuilder<?, ?> builder = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(dashScopeMessages)
                .incrementalOutput(true)
                .enableSearch(enableSearch)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE);

        applyOptions(builder, prompt.getOptions());

        try {
            Flowable<GenerationResult> flowable = generation.streamCall(builder.build());
            return convertToFlux(flowable);
        } catch (Exception e) {
            return Flux.error(new RuntimeException("DashScope API stream call failed", e));
        }
    }

    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .topP(topP)
                .build();
    }

    // -------------------------------------------------------------------------
    // Internal conversion methods
    // -------------------------------------------------------------------------

    /**
     * Convert Spring AI messages to DashScope messages.
     */
    private List<Message> convertMessages(List<org.springframework.ai.chat.messages.Message> springMessages) {
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
                Message.MessageBuilder msgBuilder = Message.builder()
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
     * Apply Spring AI chat options (tools, temperature, etc.) to the DashScope param builder.
     */
    private void applyOptions(GenerationParam.GenerationParamBuilder<?, ?> builder, ChatOptions options) {
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
     * Convert a DashScope GenerationResult to a Spring AI ChatResponse.
     */
    private ChatResponse convertToChatResponse(GenerationResult result) {
        if (result.getOutput() == null || result.getOutput().getChoices() == null
                || result.getOutput().getChoices().isEmpty()) {
            return new ChatResponse(List.of());
        }

        GenerationOutput.Choice choice = result.getOutput().getChoices().get(0);
        Message message = choice.getMessage();

        List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(message);

        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(message.getContent() != null ? message.getContent() : "")
                .toolCalls(toolCalls)
                .build();

        ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
                .finishReason(choice.getFinishReason())
                .build();

        ChatResponseMetadata responseMetadata = buildResponseMetadata(result);

        return new ChatResponse(
                List.of(new org.springframework.ai.chat.model.Generation(assistantMessage, metadata)),
                responseMetadata);
    }

    /**
     * Convert a DashScope Flowable stream to a Reactor Flux.
     *
     * <p>Text content deltas are emitted immediately for true streaming.
     * Tool call chunks are accumulated internally and emitted as a single
     * complete response at the end of the stream (when finish_reason arrives).
     *
     * <p>If the stream contains tool calls, text deltas are suppressed and only
     * the final aggregated response (with tool calls) is emitted, so that Spring
     * AI's MessageAggregator can properly detect and execute tool calls.
     */
    private Flux<ChatResponse> convertToFlux(Flowable<GenerationResult> flowable) {
        return Flux.<ChatResponse>create(sink -> {
            // Accumulator for tool call chunks across the stream
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

                    // If this chunk has tool calls, skip text emission
                    if (hasToolCalls) {
                        continue;
                    }

                    // Text-only stream: emit each delta immediately for true streaming
                    String content = (msg.getContent() != null) ? msg.getContent() : "";
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
                            ? buildResponseMetadata(lastResult)
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
                            ? buildResponseMetadata(lastResult)
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
     * Extract tool calls from a DashScope message.
     */
    private List<AssistantMessage.ToolCall> extractToolCalls(Message message) {
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
     * Build ChatResponseMetadata from a DashScope GenerationResult.
     */
    private ChatResponseMetadata buildResponseMetadata(GenerationResult result) {
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

package org.hongxi.babi.agent.config;

public record ChatProperties(
        String model,
        String fallbackModel,
        Double temperature,
        Double topP,
        boolean enableSearch
) {
}

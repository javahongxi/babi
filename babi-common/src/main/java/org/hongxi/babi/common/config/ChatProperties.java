package org.hongxi.babi.common.config;

public record ChatProperties(
        String model,
        String fallbackModel,
        Double temperature,
        Double topP,
        boolean enableSearch
) {
}

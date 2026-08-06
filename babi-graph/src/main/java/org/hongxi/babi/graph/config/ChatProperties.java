package org.hongxi.babi.graph.config;

public record ChatProperties(
        String model,
        Double temperature,
        Double topP,
        boolean enableSearch
) {
}

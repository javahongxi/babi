package org.hongxi.babi.spring.config;

public record ChatProperties(
        String model,
        Double temperature,
        Double topP,
        boolean enableSearch
) {
}

package br.com.innitisoftware.financeiro.finance_ai_copilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.ai.ingestion")
public record AiProperties(Duration fixedDelay, int batchSize, Duration requestDelay) {
    public AiProperties {
        if (fixedDelay == null) fixedDelay = Duration.ofMinutes(5);
        if (batchSize <= 0) batchSize = 100;
        if (requestDelay == null) requestDelay = Duration.ofSeconds(1);
        if (requestDelay.isNegative()) throw new IllegalArgumentException("requestDelay não pode ser negativo");
    }
}

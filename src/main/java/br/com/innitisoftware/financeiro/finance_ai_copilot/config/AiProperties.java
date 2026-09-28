package br.com.innitisoftware.financeiro.finance_ai_copilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.ai.ingestion")
public record AiProperties(Duration fixedDelay, int lookbackDays, int batchSize) {
    public AiProperties {
        if (fixedDelay == null) fixedDelay = Duration.ofMinutes(5);
        if (lookbackDays <= 0) lookbackDays = 2;
        if (batchSize <= 0) batchSize = 100;
    }
}

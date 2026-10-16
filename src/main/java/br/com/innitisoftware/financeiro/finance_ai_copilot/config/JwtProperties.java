package br.com.innitisoftware.financeiro.finance_ai_copilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Holds the shared signing secret and expected issuer for incoming bearer tokens. */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, String issuer) {
}

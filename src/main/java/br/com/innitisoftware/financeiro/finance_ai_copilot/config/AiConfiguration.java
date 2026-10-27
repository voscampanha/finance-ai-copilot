package br.com.innitisoftware.financeiro.finance_ai_copilot.config;

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
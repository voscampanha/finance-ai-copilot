package br.com.innitisoftware.financeiro.finance_ai_copilot.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LegacyTransactionDto(
        @JsonProperty("id")
        String id,

        @JsonProperty("property_id")
        String propertyId,

        @JsonProperty("description")
        String description,

        @JsonProperty("value")
        BigDecimal value,

        @JsonProperty("date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate date,

        @JsonProperty("created_at")
        LocalDateTime createdAt,

        @JsonProperty("updated_at")
        LocalDateTime updatedAt,

        @JsonProperty("category_id")
        String categoryId,

        @JsonProperty("finance_account_id")
        String financeAccountId,

        @JsonProperty("tags")
        String tags,

        @JsonProperty("attachment")
        String attachment
) {
}
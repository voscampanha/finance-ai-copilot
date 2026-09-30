package br.com.innitisoftware.financeiro.finance_ai_copilot.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TransactionEnrichedDTO(
        @JsonProperty("transaction_id") Long transactionId,
        @JsonProperty("property_id") Long propertyId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("user_name") String userName,
        @JsonProperty("description") String description,
        @JsonProperty("value") BigDecimal value,
        @JsonProperty("transaction_date") LocalDate transactionDate,
        @JsonProperty("tags") String tags,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("category_type") String categoryType,
        @JsonProperty("group_name") String groupName,
        @JsonProperty("account_name") String accountName,
        @JsonProperty("updated_at") OffsetDateTime updatedAt
) {
}
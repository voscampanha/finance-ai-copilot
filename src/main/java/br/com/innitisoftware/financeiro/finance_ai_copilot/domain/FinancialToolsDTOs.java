package br.com.innitisoftware.financeiro.finance_ai_copilot.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class FinancialToolsDTOs {

    public record CategoryStatsRequest(
        @JsonPropertyDescription("Nome da categoria ou grupo (ex: Supermercado, Alimentação, Moradia)")
        String categoryName,
        
        @JsonPropertyDescription("Data inicial para análise no formato YYYY-MM-DD")
        String startDate,
        
        @JsonPropertyDescription("Data final para análise no formato YYYY-MM-DD")
        String endDate,
        
        @JsonPropertyDescription("ID do escopo/propriedade do usuário logado")
        Long propertyId
    ) {}

    public record CategoryStatsResponse(
        String categoryName,
        long totalTransactions,
        double totalValue,
        double averageValue,
        String frequencyPattern,
        String analysis
    ) {}
}
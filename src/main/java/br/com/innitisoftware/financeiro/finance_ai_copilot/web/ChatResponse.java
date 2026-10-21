package br.com.innitisoftware.financeiro.finance_ai_copilot.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ChatResponse(
                @JsonPropertyDescription("Resposta objetiva em português do Brasil para a pergunta do usuário.") 
                String answer,

                @JsonPropertyDescription("Lista de insights ou observações adicionais extraídas dos dados") 
                List<String> insights,

                @JsonPropertyDescription("Lista de objetos contendo os dados tabulares retornados pelo banco") 
                List<DadoFinanceiro> data) {
        public record DadoFinanceiro(
                        @JsonPropertyDescription("Data no formato DD/MM/YYYY") 
                        String data,
                        @JsonPropertyDescription("Descrição da transação") 
                        String descricao,
                        @JsonPropertyDescription("Categoria da transação") 
                        String categoria,
                        @JsonPropertyDescription("Valor numérico da transação") 
                        Double valor) {
        }
}

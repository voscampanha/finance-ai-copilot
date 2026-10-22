package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Lancamento(String descricao, BigDecimal valor, LocalDate data, String categoria) {
}

package br.com.innitisoftware.financeiro.finance_ai_copilot.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialEntry(
        UUID id,
        String usuarioId,
        LocalDate data,
        BigDecimal valor,
        EntryType tipo,
        String categoria,
        String descricao) {

    public enum EntryType { GASTO, GANHO }
}

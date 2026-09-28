package br.com.innitisoftware.financeiro.finance_ai_copilot.ingestion;

import br.com.innitisoftware.financeiro.finance_ai_copilot.domain.FinancialEntry;

import java.time.Instant;
import java.util.List;

public interface LegacyFinancialReader {
    List<FinancialEntry> findUpdatedSince(Instant updatedSince);
}

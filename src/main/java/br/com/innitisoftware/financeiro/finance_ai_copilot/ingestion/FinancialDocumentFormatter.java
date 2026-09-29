package br.com.innitisoftware.financeiro.finance_ai_copilot.ingestion;

import br.com.innitisoftware.financeiro.finance_ai_copilot.domain.LegacyTransactionDto;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class FinancialDocumentFormatter {
    private final PrivacySanitizer sanitizer;

    public FinancialDocumentFormatter(PrivacySanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public Document toDocument(LegacyTransactionDto entry) {
        String text = "Transação: Data " + Objects.toString(entry.date(), "não informada")
                + " | Valor R$ " + (entry.value() == null ? "não informado" : entry.value().toPlainString())
                + " | Categoria ID: " + Objects.toString(entry.categoryId(), "não informada")
                + " | Descrição: " + sanitizer.sanitize(entry.description());
        if (entry.tags() != null && !entry.tags().isBlank()) {
            text += " | Tags: " + sanitizer.sanitize(entry.tags());
        }

        Map<String, Object> metadata = new HashMap<>();
        putIfNotNull(metadata, "propertyId", entry.propertyId());
        putIfNotNull(metadata, "data", entry.date() == null ? null : entry.date().toString());
        putIfNotNull(metadata, "categoryId", entry.categoryId());
        putIfNotNull(metadata, "financeAccountId", entry.financeAccountId());
        putIfNotNull(metadata, "sourceId", entry.id());
        return new Document(text, metadata);
    }

    private static void putIfNotNull(Map<String, Object> metadata, String key, String value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}

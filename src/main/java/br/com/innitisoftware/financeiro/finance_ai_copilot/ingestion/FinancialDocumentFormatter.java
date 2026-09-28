package br.com.innitisoftware.financeiro.finance_ai_copilot.ingestion;

import br.com.innitisoftware.financeiro.finance_ai_copilot.domain.FinancialEntry;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class FinancialDocumentFormatter {
    private final PrivacySanitizer sanitizer;

    public FinancialDocumentFormatter(PrivacySanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public Document format(FinancialEntry entry) {
        String text = "Registro Financeiro (" + entry.tipo() + "): Data " + entry.data()
                + " | Valor R$ " + entry.valor() + " | Categoria: " + entry.categoria()
                + " | Descrição: " + sanitizer.sanitize(entry.descricao());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("usuarioId", entry.usuarioId());
        metadata.put("data", entry.data().toString());
        metadata.put("tipo", entry.tipo().name());
        metadata.put("categoria", entry.categoria());
        metadata.put("sourceId", entry.id().toString());
        return new Document(text, metadata);
    }
}

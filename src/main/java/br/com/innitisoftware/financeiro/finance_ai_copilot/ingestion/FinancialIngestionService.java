package br.com.innitisoftware.financeiro.finance_ai_copilot.ingestion;

import br.com.innitisoftware.financeiro.finance_ai_copilot.config.AiProperties;
import br.com.innitisoftware.financeiro.finance_ai_copilot.domain.LegacyTransactionDto;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class FinancialIngestionService {
    private final LegacyTransactionReader reader;
    private final FinancialDocumentFormatter formatter;
    private final VectorStore vectorStore;
    private final AiProperties properties;

    public FinancialIngestionService(LegacyTransactionReader reader,
                                     FinancialDocumentFormatter formatter,
                                     VectorStore vectorStore,
                                     AiProperties properties) {
        this.reader = reader;
        this.formatter = formatter;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    // @Scheduled(fixedDelayString = "${app.ai.ingestion.fixed-delay}", initialDelayString = "PT30S")
    // public void scheduledSync() {
    //     sync();
    // }

    public int sync() {
        List<LegacyTransactionDto> entries = reader.findUpdatedSince(
                Instant.now().minus(properties.lookbackDays(), ChronoUnit.DAYS));
        for (int start = 0; start < entries.size(); start += properties.batchSize()) {
            List<Document> batch = entries.subList(start, Math.min(start + properties.batchSize(), entries.size()))
                    .stream().map(formatter::toDocument).toList();
            vectorStore.add(batch);
        }
        return entries.size();
    }
}

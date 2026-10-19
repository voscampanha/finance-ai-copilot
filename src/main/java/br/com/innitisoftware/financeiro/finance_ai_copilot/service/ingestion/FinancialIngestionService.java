package br.com.innitisoftware.financeiro.finance_ai_copilot.service.ingestion;

import br.com.innitisoftware.financeiro.finance_ai_copilot.config.AiProperties;
import br.com.innitisoftware.financeiro.finance_ai_copilot.domain.TransactionEnrichedDTO;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FinancialIngestionService {
    private final LegacyTransactionReader reader;
    private final ProcessingCheckpointRepository checkpointRepository;
    private final TransactionDocumentFormatter formatter;
    private final VectorStore vectorStore;
    private final AiProperties properties;

    public FinancialIngestionService(LegacyTransactionReader reader,
                                     ProcessingCheckpointRepository checkpointRepository,
                                     TransactionDocumentFormatter formatter,
                                     VectorStore vectorStore,
                                     AiProperties properties) {
        this.reader = reader;
        this.checkpointRepository = checkpointRepository;
        this.formatter = formatter;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    // @Scheduled(fixedDelayString = "${app.ai.ingestion.fixed-delay}", initialDelayString = "PT30S")
    // public void scheduledSync() {
    //     sync();
    // }

    public int sync() {
        ProcessingCheckpointRepository.ProcessingCheckpoint checkpoint = checkpointRepository.load();
        List<TransactionEnrichedDTO> entries = reader.findAfter(
                checkpoint.lastProcessedAt(), checkpoint.lastProcessedId());
        for (TransactionEnrichedDTO entry : entries) {
            if (entry.transactionId() == null || entry.updatedAt() == null) {
                throw new IllegalStateException(
                        "Transação sem ID ou updated_at; não é possível avançar o checkpoint com segurança");
            }
        }

        for (int start = 0; start < entries.size(); start += properties.batchSize()) {
            List<TransactionEnrichedDTO> transactionBatch = entries.subList(
                    start, Math.min(start + properties.batchSize(), entries.size()));
            List<Document> batch = transactionBatch.stream().map(formatter::toDocument).toList();
            vectorStore.add(batch);
            TransactionEnrichedDTO lastProcessed = transactionBatch.get(transactionBatch.size() - 1);
            checkpointRepository.save(
                    lastProcessed.updatedAt().toInstant(),
                    lastProcessed.transactionId());
            // if (start + properties.batchSize() < entries.size() && !properties.requestDelay().isZero()) {
            //     try {
            //         Thread.sleep(properties.requestDelay());
            //     } catch (InterruptedException exception) {
            //         Thread.currentThread().interrupt();
            //         throw new IllegalStateException("Ingestão interrompida durante a pausa entre lotes", exception);
            //     }
            // }
        }
        return entries.size();
    }
}

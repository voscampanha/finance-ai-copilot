package br.com.innitisoftware.financeiro.finance_ai_copilot.service.ingestion;

import br.com.innitisoftware.financeiro.finance_ai_copilot.config.AiProperties;
import br.com.innitisoftware.financeiro.finance_ai_copilot.domain.TransactionEnrichedDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialIngestionServiceTest {
    @Test
    void syncReadsFromCheckpointAndSavesProgressAfterEachBatch() {
        LegacyTransactionReader reader = mock(LegacyTransactionReader.class);
        ProcessingCheckpointRepository checkpointRepository = mock(ProcessingCheckpointRepository.class);
        TransactionDocumentFormatter formatter = mock(TransactionDocumentFormatter.class);
        VectorStore vectorStore = mock(VectorStore.class);
        Instant checkpointTime = Instant.parse("2026-10-18T00:00:00Z");
        var checkpoint = new ProcessingCheckpointRepository.ProcessingCheckpoint(checkpointTime, 40L);
        TransactionEnrichedDTO first = transaction(41L, "2026-10-18T01:00:00Z");
        TransactionEnrichedDTO second = transaction(42L, "2026-10-18T01:00:00Z");
        Document firstDocument = mock(Document.class);
        Document secondDocument = mock(Document.class);

        when(checkpointRepository.load()).thenReturn(checkpoint);
        when(reader.findAfter(checkpointTime, 40L)).thenReturn(List.of(first, second));
        when(formatter.toDocument(first)).thenReturn(firstDocument);
        when(formatter.toDocument(second)).thenReturn(secondDocument);

        FinancialIngestionService service = new FinancialIngestionService(
                reader,
                checkpointRepository,
                formatter,
                vectorStore,
                new AiProperties(Duration.ofMinutes(5), 1, Duration.ZERO));

        assertEquals(2, service.sync());

        var inOrder = inOrder(vectorStore, checkpointRepository);
        inOrder.verify(vectorStore).add(List.of(firstDocument));
        inOrder.verify(checkpointRepository).save(first.updatedAt().toInstant(), first.transactionId());
        inOrder.verify(vectorStore).add(List.of(secondDocument));
        inOrder.verify(checkpointRepository).save(second.updatedAt().toInstant(), second.transactionId());
    }

    private static TransactionEnrichedDTO transaction(long id, String updatedAt) {
        return new TransactionEnrichedDTO(
                id, null, null, null, null, null, null, null, null, null, null, null,
                OffsetDateTime.ofInstant(Instant.parse(updatedAt), ZoneOffset.UTC));
    }
}

package br.com.innitisoftware.financeiro.finance_ai_copilot.service.ingestion;

import br.com.innitisoftware.financeiro.finance_ai_copilot.domain.TransactionEnrichedDTO;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class TransactionDocumentFormatter {
    private static final String NOT_INFORMED = "Não informado";
    private static final Pattern CPF = Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");
    private static final Pattern CNPJ = Pattern.compile("\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b");
    private static final Pattern CARD_NUMBER = Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");

    public Document toDocument(TransactionEnrichedDTO dto) {
        String description = sanitizeDescription(dto.description());
        String content = "Lançamento Financeiro [Tipo: " + textOrNotInformed(dto.categoryType())
            + "]. Responsável: " + textOrNotInformed(dto.userName())
            + ". Data: " + textOrNotInformed(dto.transactionDate())
                + ". Valor: " + formatCurrency(dto.value())
                + ". Grupo: " + textOrNotInformed(dto.groupName())
                + " | Categoria: " + textOrNotInformed(dto.categoryName())
                + ". Conta/Meio de Pagamento: " + textOrNotInformed(dto.accountName())
                + ". Descrição/Estabelecimento: " + description
                + ". Tags: " + textOrNotInformed(dto.tags()) + ".";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("transaction_id", dto.transactionId() == null ? "" : dto.transactionId());
        metadata.put("property_id", dto.propertyId() == null ? "" : dto.propertyId().toString());
        metadata.put("user_id", dto.userId() == null ? "" : dto.userId().toString());
        metadata.put("date", dto.transactionDate() == null ? "" : dto.transactionDate().toString());
        metadata.put("value", dto.value() == null ? "" : dto.value().doubleValue());
        metadata.put("group_name", dto.groupName() == null ? "" : dto.groupName());
        metadata.put("category_name", dto.categoryName() == null ? "" : dto.categoryName());
        metadata.put("account_name", dto.accountName() == null ? "" : dto.accountName());
        metadata.put("description", description);
        metadata.put("tags", dto.tags() == null ? "" : dto.tags());
        metadata.put("category_type", dto.categoryType() == null ? "" : dto.categoryType());
        metadata.put("user_name", dto.userName() == null ? "" : dto.userName());

        return new Document(content, metadata);
    }

    private static String sanitizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return NOT_INFORMED;
        }

        return CARD_NUMBER.matcher(
                CNPJ.matcher(CPF.matcher(description).replaceAll("[CPF removido]")).replaceAll("[CNPJ removido]")
        ).replaceAll("[número de cartão removido]");
    }

    private static String textOrNotInformed(Object value) {
        if (value == null) {
            return NOT_INFORMED;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? NOT_INFORMED : text;
    }

    private static String formatCurrency(BigDecimal value) {
        if (value == null) {
            return NOT_INFORMED;
        }
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
        return currencyFormat.format(value);
    }
}
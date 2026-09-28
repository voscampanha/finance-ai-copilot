package br.com.innitisoftware.financeiro.finance_ai_copilot.ingestion;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class PrivacySanitizer {
    private static final Pattern CPF = Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");
    private static final Pattern CNPJ = Pattern.compile("\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b");
    private static final Pattern CARD_OR_ACCOUNT = Pattern.compile("\\b(?:\\d[ -]?){8,19}\\b");

    public String sanitize(String value) {
        if (value == null || value.isBlank()) return "(sem descrição)";
        return CARD_OR_ACCOUNT.matcher(CNPJ.matcher(CPF.matcher(value).replaceAll("[CPF removido]")).replaceAll("[CNPJ removido]")).replaceAll("[identificador removido]");
    }
}

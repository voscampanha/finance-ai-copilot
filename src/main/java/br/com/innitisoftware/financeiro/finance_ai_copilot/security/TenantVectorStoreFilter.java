package br.com.innitisoftware.financeiro.finance_ai_copilot.security;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.regex.Pattern;

@Component
public class TenantVectorStoreFilter {
    private static final Pattern SAFE_METADATA_VALUE = Pattern.compile("^[A-Za-z0-9._:@-]{1,128}$");

    public TenantVectorStoreFilter() {
    }

    public String forUserProperty(String propertyId) {
        if (!SAFE_METADATA_VALUE.matcher(propertyId).matches()) {
            throw new IllegalStateException("Identidade do usuário contém formato inválido");
        }
        return "property_id == '" + propertyId + "'";
    }

    public String forUserAndCategory(String propertyId, String categoria) {
        String filter = forUserProperty(propertyId);
        if (categoria != null && !categoria.isBlank()) {
            filter += " AND category_name == '" + categoria.replace("'", "''") + "'";
        }
        return filter;
    }

    public String forUserCategoryAndDateRange(String propertyId, String categoria,
                                              String dataInicial, String dataFinal) {
        String filter = forUserAndCategory(propertyId, categoria);
        if (dataInicial != null && !dataInicial.isBlank()) {
            filter += " AND date >= '" + LocalDate.parse(dataInicial) + "'";
        }
        if (dataFinal != null && !dataFinal.isBlank()) {
            filter += " AND date <= '" + LocalDate.parse(dataFinal) + "'";
        }
        return filter;
    }
}
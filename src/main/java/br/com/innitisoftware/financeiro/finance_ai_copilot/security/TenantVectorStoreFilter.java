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
}
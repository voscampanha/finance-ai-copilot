package br.com.innitisoftware.financeiro.finance_ai_copilot.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class TenantVectorStoreFilter {
    private static final Pattern SAFE_METADATA_VALUE = Pattern.compile("^[A-Za-z0-9._:@-]{1,128}$");
    private final UserIdentity userIdentity;

    public TenantVectorStoreFilter(UserIdentity userIdentity) {
        this.userIdentity = userIdentity;
    }

    public String forAuthenticatedUser() {
        String usuarioId = userIdentity.authenticatedUserId();
        if (!SAFE_METADATA_VALUE.matcher(usuarioId).matches()) {
            throw new IllegalStateException("Identidade do usuário contém formato inválido");
        }
        return "usuarioId == '" + usuarioId + "'";
    }

    public String forAuthenticatedUserAndCategory(String categoria) {
        String filter = forAuthenticatedUser();
        if (categoria != null && !categoria.isBlank()) {
            filter += " AND categoria == '" + categoria.replace("'", "''") + "'";
        }
        return filter;
    }
}
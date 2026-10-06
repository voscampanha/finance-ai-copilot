package br.com.innitisoftware.financeiro.finance_ai_copilot.security;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.regex.Pattern;

@Component
public class TenantVectorStoreFilter {
    private static final Pattern SAFE_METADATA_VALUE = Pattern.compile("^[A-Za-z0-9._:@-]{1,128}$");
    private final UserIdentity userIdentity;

    public TenantVectorStoreFilter(UserIdentity userIdentity) {
        this.userIdentity = userIdentity;
    }

    public String forAuthenticatedUser() {
        return forUser(userIdentity.authenticatedUserId());
    }

    public String forUser(String usuarioId) {
        if (!SAFE_METADATA_VALUE.matcher(usuarioId).matches()) {
            throw new IllegalStateException("Identidade do usuário contém formato inválido");
        }
        return "user_id == '" + usuarioId + "'";
    }

    public String forAuthenticatedUserAndCategory(String categoria) {
        return forUserAndCategory(userIdentity.authenticatedUserId(), categoria);
    }

    public String forUserAndCategory(String usuarioId, String categoria) {
        String filter = forUser(usuarioId);
        if (categoria != null && !categoria.isBlank()) {
            filter += " AND category_name == '" + categoria.replace("'", "''") + "'";
        }
        return filter;
    }

    public String forUserCategoryAndDateRange(String usuarioId, String categoria,
                                              String dataInicial, String dataFinal) {
        String filter = forUserAndCategory(usuarioId, categoria);
        if (dataInicial != null && !dataInicial.isBlank()) {
            filter += " AND date >= '" + LocalDate.parse(dataInicial) + "'";
        }
        if (dataFinal != null && !dataFinal.isBlank()) {
            filter += " AND date <= '" + LocalDate.parse(dataFinal) + "'";
        }
        return filter;
    }
}
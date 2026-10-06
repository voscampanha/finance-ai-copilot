package br.com.innitisoftware.financeiro.finance_ai_copilot.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantVectorStoreFilterTest {
    @Test
    void filtersByAuthenticatedUser() {
        TenantVectorStoreFilter filter = new TenantVectorStoreFilter(() -> "tenant-42");

        assertEquals("user_id == 'tenant-42'", filter.forAuthenticatedUser());
    }

    @Test
    void filtersByAuthenticatedUserAndEscapesCategory() {
        TenantVectorStoreFilter filter = new TenantVectorStoreFilter(() -> "tenant-42");

        assertEquals("user_id == 'tenant-42' AND category_name == 'Jo''s'",
                filter.forAuthenticatedUserAndCategory("Jo's"));
    }

    @Test
    void rejectsUnsafeAuthenticatedUserId() {
        TenantVectorStoreFilter filter = new TenantVectorStoreFilter(() -> "tenant' OR true");

        assertThrows(IllegalStateException.class, filter::forAuthenticatedUser);
    }
}
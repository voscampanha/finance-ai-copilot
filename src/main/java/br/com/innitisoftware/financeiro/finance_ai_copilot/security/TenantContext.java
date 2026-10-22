package br.com.innitisoftware.financeiro.finance_ai_copilot.security;

import org.springframework.stereotype.Component;

@Component
public class TenantContext {

    private final ThreadLocal<Long> propertyId = new ThreadLocal<>();

    public void set(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("propertyId não pode ser nulo");
        }
        propertyId.set(value);
    }

    public long requirePropertyId() {
        Long value = propertyId.get();
        if (value == null) {
            throw new IllegalStateException("Property ID ausente no contexto do tenant.");
        }
        return value;
    }

    public void clear() {
        propertyId.remove();
    }
}

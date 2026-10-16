package br.com.innitisoftware.financeiro.finance_ai_copilot.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import br.com.innitisoftware.financeiro.finance_ai_copilot.domain.UsuarioAutenticado;
import br.com.innitisoftware.financeiro.finance_ai_copilot.domain.exception.UsuarioNaoAutenticadoException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/** Exposes user identity and profile claims from the current authenticated JWT. */
@Service
public class CurrentUserService {

    public UsuarioAutenticado getUsuarioAtual() {
        var jwt = getJwtAtual();
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalStateException("A claim 'sub' do JWT não contém um ID de usuário.");
        }

        Long id;
        try {
            id = Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "A claim 'sub' do JWT deve conter um ID numérico de usuário.", exception);
        }

        // TODO: Para dados ausentes no JWT (por exemplo, endereço), consultar
        // /interno/usuarios/{id} no Sistema A com a API Key compartilhada.
        return new UsuarioAutenticado(
                id,
                stringClaim(jwt.getClaims().get("nome")),
                stringClaim(jwt.getClaims().get("email")),
                stringRoles(jwt.getClaims().get("roles")));
    }

    public long getPropertyIdAtual() {
        Object propertyIdClaim = getJwtAtual().getClaims().get("property_id");
        if (propertyIdClaim == null) {
            throw new IllegalStateException("A claim 'property_id' não está presente no JWT.");
        }

        try {
            if (propertyIdClaim instanceof Number number) {
                return new BigDecimal(number.toString()).longValueExact();
            }
            if (propertyIdClaim instanceof String value) {
                return Long.parseLong(value);
            }
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalStateException(
                    "A claim 'property_id' do JWT deve conter um ID inteiro.", exception);
        }
        throw new IllegalStateException("A claim 'property_id' do JWT deve conter um ID inteiro.");
    }

    private Jwt getJwtAtual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new UsuarioNaoAutenticadoException();
        }
        return jwtAuthentication.getToken();
    }

    private static String stringClaim(Object claim) {
        return claim instanceof String value ? value : null;
    }

    private static List<String> stringRoles(Object rolesClaim) {
        if (!(rolesClaim instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}

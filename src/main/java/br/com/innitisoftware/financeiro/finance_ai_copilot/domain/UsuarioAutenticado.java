package br.com.innitisoftware.financeiro.finance_ai_copilot.domain;

import java.util.List;

public record UsuarioAutenticado(Long id, String nome, String email, List<String> roles) {
}

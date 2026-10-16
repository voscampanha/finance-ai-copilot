package br.com.innitisoftware.financeiro.finance_ai_copilot.domain.exception;

public class UsuarioNaoAutenticadoException extends RuntimeException {
    public UsuarioNaoAutenticadoException() {
        super("Não há usuário autenticado no contexto de segurança.");
    }
}

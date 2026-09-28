package br.com.innitisoftware.financeiro.finance_ai_copilot.web;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String question) {}

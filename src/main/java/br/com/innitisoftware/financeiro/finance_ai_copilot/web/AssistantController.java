package br.com.innitisoftware.financeiro.finance_ai_copilot.web;

import br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.FinancialAdvisorService;
import br.com.innitisoftware.financeiro.finance_ai_copilot.ingestion.FinancialIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AssistantController {
    private final FinancialAdvisorService advisor;
    private final FinancialIngestionService ingestion;

    public AssistantController(FinancialAdvisorService advisor, FinancialIngestionService ingestion) {
        this.advisor = advisor;
        this.ingestion = ingestion;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(advisor.answer(request.question()));
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Integer>> sync() {
        return ResponseEntity.ok(Map.of("synchronizedEntries", ingestion.sync()));
    }
}

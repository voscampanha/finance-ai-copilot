package br.com.innitisoftware.financeiro.finance_ai_copilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
	@TestPropertySource(properties = {
			"spring.ai.google.genai.api-key=test-key",
			"spring.ai.google.genai.embedding.api-key=test-key"
	})
class FinanceAiCopilotApplicationTests {

	@Test
	void contextLoads() {
	}

}

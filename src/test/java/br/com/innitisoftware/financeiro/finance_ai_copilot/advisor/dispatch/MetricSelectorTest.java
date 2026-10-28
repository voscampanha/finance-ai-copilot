package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.dispatch;

import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch.MetricSelection;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch.MetricSelector;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricDescriptor;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.ParamDescriptor;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.ParamType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricSelectorTest {

    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec requestSpec =
            mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
    private final ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MetricSelector selector = new MetricSelector(chatClient, objectMapper, Clock.systemUTC());
    private final List<MetricDescriptor> catalog = List.of(new MetricDescriptor(
            "total_gasto_por_categoria",
            "Total por categoria",
            List.of(
                    new ParamDescriptor("data_inicio", ParamType.DATE, true, "Data inicial"),
                    new ParamDescriptor("data_fim", ParamType.DATE, true, "Data final"))));

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
    }

    @Test
    void validJsonProducesMetricSelection() {
        when(responseSpec.content()).thenReturn(
                "{\"metric\":\"total_gasto_por_categoria\",\"params\":{\"data_inicio\":\"2026-10-01\"}}");

        assertThat(selector.trySelect("Total gasto", catalog))
                .contains(new MetricSelection(
                        "total_gasto_por_categoria",
                        Map.of("data_inicio", "2026-10-01")));
        verify(requestSpec).system(argThat((String prompt) ->
                prompt.contains("total_gasto_por_categoria")
                        && !prompt.contains("SELECT")));
    }

    @Test
    void invalidJsonProducesEmptySelection() {
        when(responseSpec.content()).thenReturn("não é json");

        assertThat(selector.trySelect("Total gasto", catalog)).isEmpty();
    }

    @Test
    void unknownMetricProducesEmptySelection() {
        when(responseSpec.content()).thenReturn("{\"metric\":\"metric_inexistente\",\"params\":{}}");

        assertThat(selector.trySelect("Total gasto", catalog)).isEmpty();
    }

    @Test
    void currentMonthDatesAreResolvedByApplicationInsteadOfModel() {
        when(responseSpec.content()).thenReturn(
                "{\"metric\":\"total_gasto_por_categoria\",\"params\":{" +
                        "\"data_inicio\":\"2026-05-01\",\"data_fim\":\"2026-05-07\"," +
                        "\"categoria\":\"Mercado\"}}");

        LocalDate today = LocalDate.now();
        assertThat(selector.trySelect("Qual total gasto com mercado este mês?", catalog))
                .contains(new MetricSelection(
                        "total_gasto_por_categoria",
                        Map.of(
                                "data_inicio", today.withDayOfMonth(1).toString(),
                                "data_fim", today.toString(),
                                "categoria", "Mercado")));
    }

    @Test
    void currentDateAndRelativePeriodRulesAreIncludedInPrompt() {
        when(responseSpec.content()).thenReturn(
                "{\"metric\":\"total_gasto_por_categoria\",\"params\":{}}");
        MetricSelector fixedSelector = selectorAt(LocalDate.of(2026, 10, 28));

        fixedSelector.trySelect("Quanto gastei este ano?", catalog);

        verify(requestSpec).system((String) argThat((String prompt) ->
                prompt.contains("Data atual: 2026-10-28")
                        && prompt.contains("\"este ano\"")
                        && prompt.contains("\"esta semana\"")
                        && prompt.contains("mês mencionado sem ano")));
    }

    @Test
    void currentYearDatesAreResolvedByApplicationInsteadOfModel() {
        when(responseSpec.content()).thenReturn(
                "{\"metric\":\"total_gasto_por_categoria\",\"params\":{" +
                        "\"data_inicio\":\"2025-01-01\",\"data_fim\":\"2025-12-31\"}}");

        assertThat(selectorAt(LocalDate.of(2026, 10, 28))
                .trySelect("Quanto gastei este ano?", catalog))
                .contains(new MetricSelection(
                        "total_gasto_por_categoria",
                        Map.of(
                                "data_inicio", "2026-01-01",
                                "data_fim", "2026-10-28")));
    }

    @Test
    void currentWeekDatesAreResolvedByApplicationInsteadOfModel() {
        when(responseSpec.content()).thenReturn(
                "{\"metric\":\"total_gasto_por_categoria\",\"params\":{" +
                        "\"data_inicio\":\"2025-10-01\",\"data_fim\":\"2025-10-31\"}}");

        assertThat(selectorAt(LocalDate.of(2026, 10, 28))
                .trySelect("Quanto gastei nesta semana?", catalog))
                .contains(new MetricSelection(
                        "total_gasto_por_categoria",
                        Map.of(
                                "data_inicio", "2026-10-26",
                                "data_fim", "2026-10-28")));
    }

    @Test
    void monthWithoutYearUsesCurrentYearAndFullCalendarMonth() {
        when(responseSpec.content()).thenReturn(
                "{\"metric\":\"total_gasto_por_categoria\",\"params\":{" +
                        "\"data_inicio\":\"2025-10-01\",\"data_fim\":\"2025-10-31\"}}");

        assertThat(selectorAt(LocalDate.of(2026, 10, 28))
                .trySelect("Quanto gastei em outubro?", catalog))
                .contains(new MetricSelection(
                        "total_gasto_por_categoria",
                        Map.of(
                                "data_inicio", "2026-10-01",
                                "data_fim", "2026-10-31")));
    }

    private MetricSelector selectorAt(LocalDate date) {
        Clock fixedClock = Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        return new MetricSelector(chatClient, objectMapper, fixedClock);
    }
}

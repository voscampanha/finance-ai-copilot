package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.dispatch;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantContext;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch.DispatchResult;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch.MetricSelector;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch.QueryDispatcher;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.DynamicSqlTool;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.FinancialTools;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.SemanticLayerTools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QueryDispatcherTest {

    @Test
    void routesContextualQuestionsThroughFinancialToolsWithTenantContext() {
        SemanticLayerTools semanticLayer = mock(SemanticLayerTools.class);
        MetricSelector metricSelector = mock(MetricSelector.class);
        FinancialTools financialTools = mock(FinancialTools.class);
        DynamicSqlTool dynamicSqlTool = mock(DynamicSqlTool.class);
        TenantContext tenantContext = new TenantContext();
        tenantContext.set(17L);
        String question = "O que significa esta compra?";

        when(metricSelector.trySelect(anyString(), any())).thenReturn(Optional.empty());
        when(financialTools.searchFinancialContext(anyString(), any())).thenReturn("Contexto encontrado");

        QueryDispatcher dispatcher = new QueryDispatcher(
                semanticLayer, metricSelector, financialTools, dynamicSqlTool, tenantContext);

        assertThat(dispatcher.dispatch(question))
                .isEqualTo(new DispatchResult.Contextual("Contexto encontrado"));

        ArgumentCaptor<ToolContext> toolContextCaptor = ArgumentCaptor.forClass(ToolContext.class);
        verify(financialTools).searchFinancialContext(eq(question), toolContextCaptor.capture());
        assertThat(toolContextCaptor.getValue().getContext())
                .containsEntry("propertyId", 17L);
        verifyNoInteractions(dynamicSqlTool);
    }

    @Test
    void routesNonContextualQuestionsThroughDynamicSqlToolWithTenantContext() {
        SemanticLayerTools semanticLayer = mock(SemanticLayerTools.class);
        MetricSelector metricSelector = mock(MetricSelector.class);
        FinancialTools financialTools = mock(FinancialTools.class);
        DynamicSqlTool dynamicSqlTool = mock(DynamicSqlTool.class);
        TenantContext tenantContext = new TenantContext();
        tenantContext.set(17L);
        String question = "Quanto gastei este mês?";

        when(metricSelector.trySelect(anyString(), any())).thenReturn(Optional.empty());
        when(dynamicSqlTool.executeDynamicQuery(anyString(), any())).thenReturn("Total: R$ 120,00");

        QueryDispatcher dispatcher = new QueryDispatcher(
                semanticLayer, metricSelector, financialTools, dynamicSqlTool, tenantContext);

        assertThat(dispatcher.dispatch(question))
                .isEqualTo(new DispatchResult.Fallback("Total: R$ 120,00"));

        ArgumentCaptor<ToolContext> toolContextCaptor = ArgumentCaptor.forClass(ToolContext.class);
        verify(dynamicSqlTool).executeDynamicQuery(eq(question), toolContextCaptor.capture());
        assertThat(toolContextCaptor.getValue().getContext())
                .containsExactlyEntriesOf(Map.of("propertyId", 17L));
        verifyNoInteractions(financialTools);
    }
}

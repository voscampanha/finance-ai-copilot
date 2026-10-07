package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlValidatorTest {

    private final SqlValidator validator = new SqlValidator();

    @Test
    void acceptsTenantBoundQueryForSingleDomainTable() {
        assertDoesNotThrow(() -> validator.validate("""
                SELECT t.description
                FROM transactions t
                WHERE t.property_id = :propertyId
                """));
    }

    @Test
    void acceptsBacktickQuotedGroupsTable() {
        assertDoesNotThrow(() -> validator.validate("""
                SELECT g.name
                FROM `groups` g
                WHERE g.property_id = :propertyId
                """));
    }

    @Test
    void requiresTenantPredicateForEveryJoinedTable() {
        String sql = """
                SELECT t.description, c.name
                FROM transactions t
                JOIN categories c ON c.id = t.category_id
                WHERE t.property_id = :propertyId
                """;

        assertThrows(SqlValidator.SqlValidationException.class, () -> validator.validate(sql));
    }

    @Test
    void acceptsJoinOnlyWhenEveryTableIsBoundToTrustedTenantParameter() {
        assertDoesNotThrow(() -> validator.validate("""
                SELECT t.description, c.name
                FROM transactions t
                JOIN categories c ON c.id = t.category_id
                WHERE t.property_id = :propertyId
                  AND c.property_id = :propertyId
                """));
    }

    @Test
    void rejectsTenantFilterUsingLiteralOrUntrustedParameter() {
        assertThrows(SqlValidator.SqlValidationException.class, () -> validator.validate("""
                SELECT t.description
                FROM transactions t
                WHERE t.property_id = 42
                """));

        assertThrows(SqlValidator.SqlValidationException.class, () -> validator.validate("""
                SELECT t.description
                FROM transactions t
                WHERE t.property_id = :requestedPropertyId
                """));
    }

    @Test
    void rejectsTenantFilterNestedOnlyInsideOr() {
        assertThrows(SqlValidator.SqlValidationException.class, () -> validator.validate("""
                SELECT t.description
                FROM transactions t
                WHERE t.property_id = :propertyId OR t.property_id = 42
                """));
    }

    @Test
    void rejectsSubqueriesThatCouldBypassTenantValidation() {
        assertThrows(SqlValidator.SqlValidationException.class, () -> validator.validate("""
                SELECT t.description
                FROM transactions t
                WHERE t.property_id = :propertyId
                  AND EXISTS (
                    SELECT 1
                    FROM transactions other
                    WHERE other.description = 'transfer'
                  )
                """));

        assertThrows(SqlValidator.SqlValidationException.class, () -> validator.validate("""
                SELECT t.description
                FROM transactions t
                WHERE t.property_id = :propertyId
                  AND t.category_id IN (
                    SELECT c.id
                    FROM categories c
                    WHERE c.name = 'Transferência'
                  )
                """));
    }

    @Test
    void usesPropertyIdForPropertiesTable() {
        assertDoesNotThrow(() -> validator.validate("""
                SELECT p.name
                FROM properties p
                WHERE p.id = :propertyId
                """));

        assertThrows(SqlValidator.SqlValidationException.class, () -> validator.validate("""
                SELECT p.name
                FROM properties p
                WHERE p.id = :otherPropertyId
                """));
    }

    @Test
    void rejectsRollupGroupedByKeyWhileSelectingNonAggregatedColumns() {
        assertThrows(SqlValidator.SqlValidationException.class, () -> validator.validate("""
                SELECT CASE WHEN t.id IS NULL THEN 'TOTAL' ELSE t.date END AS data,
                       CASE WHEN t.id IS NULL THEN 'TOTAL' ELSE t.description END AS descricao,
                       SUM(t.value) AS valor
                FROM transactions t
                WHERE t.property_id = :propertyId
                  AND t.date >= '2025-08-01'
                  AND t.date <= '2025-08-31'
                GROUP BY t.id WITH ROLLUP
                """));
    }

    @Test
    void acceptsRollupGroupedByEveryNonAggregatedSelectedColumn() {
        assertDoesNotThrow(() -> validator.validate("""
                SELECT CASE WHEN t.date IS NULL THEN 'TOTAL' ELSE t.date END AS data,
                       CASE WHEN t.date IS NULL THEN 'TOTAL' ELSE t.description END AS descricao,
                       SUM(t.value) AS valor
                FROM transactions t
                WHERE t.property_id = :propertyId
                GROUP BY t.date, t.description WITH ROLLUP
                """));
    }

    @Test
    void acceptsRollupGroupedBySelectedAlias() {
        assertDoesNotThrow(() -> validator.validate("""
                SELECT t.date AS data, SUM(t.value) AS valor
                FROM transactions t
                WHERE t.property_id = :propertyId
                GROUP BY data WITH ROLLUP
                """));
    }

    @Test
    void acceptsRollupWhenOnlyGroupedColumnsAndAggregatesAreSelected() {
        assertDoesNotThrow(() -> validator.validate("""
                SELECT t.id, SUM(t.value) AS valor
                FROM transactions t
                WHERE t.property_id = :propertyId
                GROUP BY t.id WITH ROLLUP
                """));

        assertDoesNotThrow(() -> validator.validate("""
                SELECT SUM(t.value) AS valor
                FROM transactions t
                WHERE t.property_id = :propertyId
                GROUP BY t.id WITH ROLLUP
                """));
    }

    @Test
    void keepsAcceptingGroupedQueriesWithoutRollup() {
        assertDoesNotThrow(() -> validator.validate("""
                SELECT t.date, t.description, SUM(t.value) AS valor
                FROM transactions t
                WHERE t.property_id = :propertyId
                GROUP BY t.id
                """));
    }
}

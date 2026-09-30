package br.com.innitisoftware.financeiro.finance_ai_copilot.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfiguration {

    @Bean
    @Primary
    @ConfigurationProperties("app.vector.datasource")
    DataSource vectorDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @ConfigurationProperties("app.legacy.datasource")
    DataSource legacyDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    NamedParameterJdbcTemplate legacyNamedParameterJdbcTemplate(
            @Qualifier("legacyDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}

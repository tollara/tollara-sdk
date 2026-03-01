package com.bugisiw.marketplace.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Configuration for usage logging.
 * This provides the necessary beans for the UsageLogRepository.
 */
@Configuration
public class UsageLogConfig {

    /**
     * Creates a data source for the usage database.
     * Properties are configured via application.yml with the usage-db prefix:
     * usage-db.jdbc-url (required by HikariCP when driver-class-name is set)
     * usage-db.username
     * usage-db.password
     * usage-db.driver-class-name
     *
     * @return DataSource configured for the usage database
     */
    @Bean
    @ConditionalOnMissingBean(name = "usageDataSource")
    @ConfigurationProperties(prefix = "usage-db")
    public DataSource usageDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * Creates a JdbcTemplate using the usage data source.
     *
     * @param dataSource the usage database data source
     * @return JdbcTemplate for the usage database
     */
    @Bean
    @ConditionalOnMissingBean(name = "usageJdbcTemplate")
    public JdbcTemplate usageJdbcTemplate(@Qualifier("usageDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
} 
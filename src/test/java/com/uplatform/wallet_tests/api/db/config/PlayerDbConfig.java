package com.uplatform.wallet_tests.api.db.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.uplatform.wallet_tests.api.db.repository.player",
        entityManagerFactoryRef = "playerEntityManagerFactory",
        transactionManagerRef = "playerTransactionManager"
)
public class PlayerDbConfig extends BaseDbConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.player")
    public DataSourceProperties playerDataSourceProperties() {
        return createDataSourceProperties();
    }

    @Bean
    public HikariDataSource playerDataSource(@Qualifier("playerDataSourceProperties") DataSourceProperties properties) {
        return createDataSource(properties);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean playerEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("playerDataSource") HikariDataSource dataSource) {
        return createEntityManagerFactory(builder, dataSource,
                "com.uplatform.wallet_tests.api.db.entity.player", "player");
    }

    @Bean
    public PlatformTransactionManager playerTransactionManager(
            @Qualifier("playerEntityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return createTransactionManager(entityManagerFactory);
    }
}

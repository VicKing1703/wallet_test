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
import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.uplatform.wallet_tests.api.db.repository.wallet",
        entityManagerFactoryRef = "walletEntityManagerFactory",
        transactionManagerRef = "walletTransactionManager"
)
public class WalletDbConfig extends BaseDbConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.wallet")
    public DataSourceProperties walletDataSourceProperties() {
        return createDataSourceProperties();
    }

    @Bean
    public DataSource walletDataSource(@Qualifier("walletDataSourceProperties") DataSourceProperties properties) {
        return createDataSource(properties);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean walletEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("walletDataSource") DataSource dataSource) {
        return createEntityManagerFactory(builder, dataSource,
                "com.uplatform.wallet_tests.api.db.entity.wallet", "wallet");
    }

    @Bean
    public PlatformTransactionManager walletTransactionManager(
            @Qualifier("walletEntityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return createTransactionManager(entityManagerFactory);
    }
}
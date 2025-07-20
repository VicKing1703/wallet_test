package com.uplatform.wallet_tests.api.db.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

public abstract class BaseDbConfig {

    protected DataSourceProperties createDataSourceProperties() {
        return new DataSourceProperties();
    }

    protected DataSource createDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    protected LocalContainerEntityManagerFactoryBean createEntityManagerFactory(EntityManagerFactoryBuilder builder,
                                                                                 DataSource dataSource,
                                                                                 String packages,
                                                                                 String persistenceUnit) {
        return builder
                .dataSource(dataSource)
                .packages(packages)
                .persistenceUnit(persistenceUnit)
                .build();
    }

    protected PlatformTransactionManager createTransactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }
}

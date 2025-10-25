package com.testing.multisource.api.db.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import com.zaxxer.hikari.HikariDataSource;

public abstract class BaseDbConfig {

    protected DataSourceProperties createDataSourceProperties() {
        return new DataSourceProperties();
    }

    protected HikariDataSource createDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    protected LocalContainerEntityManagerFactoryBean createEntityManagerFactory(EntityManagerFactoryBuilder builder,
                                                                                 HikariDataSource dataSource,
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

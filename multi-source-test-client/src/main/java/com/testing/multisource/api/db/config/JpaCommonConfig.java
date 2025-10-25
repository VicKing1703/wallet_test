package com.testing.multisource.api.db.config;

import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(HibernateJpaAutoConfiguration.class)
public class JpaCommonConfig {
}
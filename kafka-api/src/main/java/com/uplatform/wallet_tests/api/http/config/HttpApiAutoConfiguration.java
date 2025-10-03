package com.uplatform.wallet_tests.api.http.config;

import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.config.modules.http.HttpModuleProperties;
import com.uplatform.wallet_tests.config.modules.http.HttpDefaultsProperties;
import com.uplatform.wallet_tests.config.modules.http.HttpConcurrencyProperties;
import feign.Client;
import feign.Logger;
import feign.Request;
import feign.okhttp.OkHttpClient;
import okhttp3.ConnectionPool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnClass(FeignBuilderCustomizer.class)
public class HttpApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    @ConditionalOnMissingBean
    public Client feignClient() {
        okhttp3.OkHttpClient okHttpClient = new okhttp3.OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .build();

        return new OkHttpClient(okHttpClient);
    }

    @Bean(name = "httpApiLogger")
    @ConditionalOnMissingBean(name = "httpApiLogger")
    public Logger httpApiLogger() {
        return new Logger.ErrorLogger();
    }

    @Bean(name = "httpApiFeignCustomizer")
    @ConditionalOnMissingBean(name = "httpApiFeignCustomizer")
    public FeignBuilderCustomizer httpApiFeignCustomizer(@Qualifier("httpApiLogger") Logger logger,
                                                         Logger.Level feignLoggerLevel) {
        return builder -> builder
                .logger(logger)
                .logLevel(feignLoggerLevel);
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpModuleProperties httpModuleProperties(EnvironmentConfigurationProvider provider) {
        HttpModuleProperties http = provider.getEnvironmentConfig().getHttp();
        if (http == null) {
            http = new HttpModuleProperties();
            provider.getEnvironmentConfig().setHttp(http);
        }
        return http;
    }

    @Bean
    @ConditionalOnMissingBean
    public Request.Options httpRequestOptions(HttpModuleProperties httpModuleProperties) {
        long connectTimeoutMs = 10_000L;
        long readTimeoutMs = Optional.ofNullable(httpModuleProperties)
                .map(HttpModuleProperties::getDefaults)
                .map(HttpDefaultsProperties::getConcurrency)
                .map(HttpConcurrencyProperties::getRequestTimeoutMs)
                .map(Long::longValue)
                .orElse(60_000L);
        return new Request.Options(Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(readTimeoutMs), true);
    }
}

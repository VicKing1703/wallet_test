package com.uplatform.wallet_tests.api.http.config;

import feign.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.testing.multisource.api.attachment.AllureAttachmentService;
import org.springframework.context.annotation.Primary;

@Configuration
public class AllureFeignLoggerConfig {

    @Bean(name = "httpApiLogger")
    @Primary
    public Logger allureFeignLogger(AllureAttachmentService attachmentService) {
        return new AllureFeignLogger(attachmentService);
    }
}

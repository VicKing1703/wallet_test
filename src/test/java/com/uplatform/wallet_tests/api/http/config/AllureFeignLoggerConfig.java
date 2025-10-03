package com.uplatform.wallet_tests.api.http.config;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import feign.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import org.springframework.context.annotation.Primary;

@Configuration
public class AllureFeignLoggerConfig {

    @Bean(name = "httpApiLogger")
    @Primary
    public Logger allureFeignLogger(AllureAttachmentService attachmentService) {
        return new AllureFeignLogger(attachmentService);
    }
}

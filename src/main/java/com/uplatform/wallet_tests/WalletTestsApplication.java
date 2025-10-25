package com.uplatform.wallet_tests;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableFeignClients(basePackages = "com.uplatform.wallet_tests.api.http")
@ComponentScan(basePackages = {"com.uplatform.wallet_tests", "com.testing.multisource"})
public class WalletTestsApplication {

	public static void main(String[] args) {
		SpringApplication.run(WalletTestsApplication.class, args);
	}
}
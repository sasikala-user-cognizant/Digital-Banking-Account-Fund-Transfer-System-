package com.example.banking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Digital Banking API")
                .version("v1")
                .description("Customer and account management, deposits, withdrawals, "
                        + "fund transfers and transaction history. All data is synthetic.")
                .contact(new Contact().name("Case Study Team")));
    }
}

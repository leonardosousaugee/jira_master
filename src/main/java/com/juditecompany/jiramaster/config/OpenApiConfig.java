package com.juditecompany.jiramaster.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI jiraMasterOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Jira Master Service")
                .description("Microsservico que expoe operacoes de card do Jira via API REST")
                .version("1.0.0"));
    }
}

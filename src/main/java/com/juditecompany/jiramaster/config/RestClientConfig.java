package com.juditecompany.jiramaster.config;

import com.juditecompany.jiramaster.exception.JiraApiException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient jiraRestClient(JiraProperties jiraProperties) {
        String credenciais = jiraProperties.getEmail() + ":" + jiraProperties.getApiToken();
        String basicAuth = Base64.getEncoder().encodeToString(credenciais.getBytes(StandardCharsets.UTF_8));

        return RestClient.builder()
                .baseUrl(jiraProperties.getBaseUrl() + "/rest/api/3")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultStatusHandler(HttpStatusCode::isError, this::lancarJiraApiException)
                .build();
    }

    private void lancarJiraApiException(HttpRequest request, ClientHttpResponse response) throws IOException {
        String corpo = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        throw new JiraApiException(response.getStatusCode(), corpo);
    }
}

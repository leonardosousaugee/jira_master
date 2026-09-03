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

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient jiraRestClient(JiraOAuthProperties oauthProperties, JiraOAuthTokenService tokenService) {
        return RestClient.builder()
                .baseUrl("https://api.atlassian.com/ex/jira/" + oauthProperties.getCloudId() + "/rest/api/3")
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenService.obterAccessToken());
                    return execution.execute(request, body);
                })
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultStatusHandler(HttpStatusCode::isError, this::lancarJiraApiException)
                .build();
    }

    private void lancarJiraApiException(HttpRequest request, ClientHttpResponse response) throws IOException {
        String corpo = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        throw new JiraApiException(response.getStatusCode(), corpo);
    }
}

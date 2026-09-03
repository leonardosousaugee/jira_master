package com.juditecompany.jiramaster.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Credenciais do app OAuth 2.0 (3LO) registrado em developer.atlassian.com. Basic Auth com token
 * de conta (com ou sem escopo) nao aplica escopo de verdade nessa instancia — só OAuth 2.0
 * respeita os escopos granulares (`read:jira-work`, `write:jira-work`, `read:jira-user`).
 */
@Validated
@ConfigurationProperties(prefix = "jira.oauth")
public class JiraOAuthProperties {

    @NotBlank
    private String clientId;

    @NotBlank
    private String clientSecret;

    @NotBlank
    private String cloudId;

    @NotBlank
    private String refreshToken;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getCloudId() {
        return cloudId;
    }

    public void setCloudId(String cloudId) {
        this.cloudId = cloudId;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}

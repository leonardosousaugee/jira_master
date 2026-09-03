package com.juditecompany.jiramaster.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JiraOAuthPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void deveVincularTodasAsPropriedadesQuandoPresentes() {
        contextRunner
                .withPropertyValues(
                        "jira.oauth.client-id=client-id-teste",
                        "jira.oauth.client-secret=client-secret-teste",
                        "jira.oauth.cloud-id=cloud-id-teste",
                        "jira.oauth.refresh-token=refresh-token-teste")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JiraOAuthProperties props = context.getBean(JiraOAuthProperties.class);
                    assertThat(props.getClientId()).isEqualTo("client-id-teste");
                    assertThat(props.getClientSecret()).isEqualTo("client-secret-teste");
                    assertThat(props.getCloudId()).isEqualTo("cloud-id-teste");
                    assertThat(props.getRefreshToken()).isEqualTo("refresh-token-teste");
                });
    }

    @Test
    void deveFalharQuandoRefreshTokenEstaAusente() {
        contextRunner
                .withPropertyValues(
                        "jira.oauth.client-id=client-id-teste",
                        "jira.oauth.client-secret=client-secret-teste",
                        "jira.oauth.cloud-id=cloud-id-teste")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(JiraOAuthProperties.class)
    static class TestConfig {
    }
}

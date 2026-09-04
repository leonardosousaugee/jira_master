package com.juditecompany.jiramaster.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JiraPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void deveVincularTodasAsPropriedadesQuandoPresentes() {
        contextRunner
                .withPropertyValues(
                        "jira.base-url=https://juditecompany.atlassian.net",
                        "jira.email=leonardo.sousa@witzler-ultragaz.com.br",
                        "jira.api-token=token-de-teste",
                        "jira.default-project-key=KAN",
                        "jira.worker-field-name=Worker")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JiraProperties props = context.getBean(JiraProperties.class);
                    assertThat(props.getBaseUrl()).isEqualTo("https://juditecompany.atlassian.net");
                    assertThat(props.getEmail()).isEqualTo("leonardo.sousa@witzler-ultragaz.com.br");
                    assertThat(props.getApiToken()).isEqualTo("token-de-teste");
                    assertThat(props.getDefaultProjectKey()).isEqualTo("KAN");
                    assertThat(props.getWorkerFieldName()).isEqualTo("Worker");
                });
    }

    @Test
    void deveFalharQuandoApiTokenEstaAusente() {
        contextRunner
                .withPropertyValues(
                        "jira.base-url=https://juditecompany.atlassian.net",
                        "jira.email=leonardo.sousa@witzler-ultragaz.com.br",
                        "jira.default-project-key=KAN")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(JiraProperties.class)
    static class TestConfig {
    }
}

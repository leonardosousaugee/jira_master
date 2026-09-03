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
                        "jira.default-project-key=KAN",
                        "jira.subtask-issue-type-id=10002",
                        "jira.worker-field-id=customfield_10073")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JiraProperties props = context.getBean(JiraProperties.class);
                    assertThat(props.getDefaultProjectKey()).isEqualTo("KAN");
                    assertThat(props.getSubtaskIssueTypeId()).isEqualTo("10002");
                    assertThat(props.getWorkerFieldId()).isEqualTo("customfield_10073");
                });
    }

    @Test
    void deveFalharQuandoDefaultProjectKeyEstaAusente() {
        contextRunner
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(JiraProperties.class)
    static class TestConfig {
    }
}

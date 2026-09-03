package com.juditecompany.jiramaster.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jira")
public class JiraProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String email;

    @NotBlank
    private String apiToken;

    @NotBlank
    private String defaultProjectKey;

    /**
     * Id do tipo de item usado para subtarefas. Opcional: em branco, o id e descoberto
     * em runtime lendo os tipos do projeto. Serve de escape quando o projeto tem mais de
     * um tipo de subtarefa e a descoberta escolhe o errado.
     */
    private String subtaskIssueTypeId;

    /**
     * Id do campo customizado Worker (ex.: {@code customfield_10073}). Opcional: em branco, o id e
     * descoberto em runtime lendo os campos da instancia e casando pelo nome "Worker". Serve de
     * escape quando existe mais de um campo com esse nome ou o campo foi renomeado.
     */
    private String workerFieldId;

    public String getWorkerFieldId() {
        return workerFieldId;
    }

    public void setWorkerFieldId(String workerFieldId) {
        this.workerFieldId = workerFieldId;
    }

    public String getSubtaskIssueTypeId() {
        return subtaskIssueTypeId;
    }

    public void setSubtaskIssueTypeId(String subtaskIssueTypeId) {
        this.subtaskIssueTypeId = subtaskIssueTypeId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getDefaultProjectKey() {
        return defaultProjectKey;
    }

    public void setDefaultProjectKey(String defaultProjectKey) {
        this.defaultProjectKey = defaultProjectKey;
    }
}

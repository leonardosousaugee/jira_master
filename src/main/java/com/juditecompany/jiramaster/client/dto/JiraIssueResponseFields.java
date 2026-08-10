package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classe, e nao record como os demais DTOs, porque campo customizado do Jira chega com nome
 * dinamico ({@code customfield_10073}) e so {@code @JsonAnySetter} recolhe isso — anotacao que o
 * Jackson 2.17 do Spring Boot 3.3 ainda nao aceita em componente de record. Os acessores mantem o
 * nome curto de record para os pontos de uso continuarem lendo {@code fields.summary()}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class JiraIssueResponseFields {

    private final String summary;
    private final JsonNode description;
    private final JiraStatusDto status;
    private final JiraNameRef priority;
    private final JiraNameRef issuetype;
    private final JiraFieldRef project;
    private final JiraFieldRef parent;
    private final List<JiraIssueDto> subtasks;
    private final String created;
    private final String updated;
    private final Map<String, JsonNode> camposCustomizados = new LinkedHashMap<>();

    @JsonCreator
    public JiraIssueResponseFields(
            @JsonProperty("summary") String summary,
            @JsonProperty("description") JsonNode description,
            @JsonProperty("status") JiraStatusDto status,
            @JsonProperty("priority") JiraNameRef priority,
            @JsonProperty("issuetype") JiraNameRef issuetype,
            @JsonProperty("project") JiraFieldRef project,
            @JsonProperty("parent") JiraFieldRef parent,
            @JsonProperty("subtasks") List<JiraIssueDto> subtasks,
            @JsonProperty("created") String created,
            @JsonProperty("updated") String updated) {
        this.summary = summary;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.issuetype = issuetype;
        this.project = project;
        this.parent = parent;
        this.subtasks = subtasks;
        this.created = created;
        this.updated = updated;
    }

    @JsonAnySetter
    void capturarCampoCustomizado(String nome, JsonNode valor) {
        camposCustomizados.put(nome, valor);
    }

    /** Nulo quando o campo nao veio, veio nulo ou veio em branco — os tres significam "sem valor". */
    public String textoDoCampo(String fieldId) {
        if (fieldId == null) {
            return null;
        }
        JsonNode valor = camposCustomizados.get(fieldId);
        if (valor == null || valor.isNull()) {
            return null;
        }
        String texto = valor.asText().strip();
        return texto.isEmpty() ? null : texto;
    }

    public String summary() {
        return summary;
    }

    public JsonNode description() {
        return description;
    }

    public JiraStatusDto status() {
        return status;
    }

    public JiraNameRef priority() {
        return priority;
    }

    public JiraNameRef issuetype() {
        return issuetype;
    }

    public JiraFieldRef project() {
        return project;
    }

    public JiraFieldRef parent() {
        return parent;
    }

    public List<JiraIssueDto> subtasks() {
        return subtasks;
    }

    public String created() {
        return created;
    }

    public String updated() {
        return updated;
    }
}

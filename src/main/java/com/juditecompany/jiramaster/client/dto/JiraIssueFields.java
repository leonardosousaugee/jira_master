package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * {@code camposCustomizados} sai achatado no mesmo nivel dos demais campos, que e como o Jira
 * espera receber campo customizado: a chave e o id dinamico ({@code customfield_10073}), entao nao
 * ha componente proprio para declarar. Mapa vazio nao emite nada.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JiraIssueFields(
        JiraFieldRef project,
        String summary,
        Object description,
        JiraNameRef issuetype,
        JiraFieldRef parent,
        JiraNameRef priority,
        @JsonAnyGetter Map<String, Object> camposCustomizados
) {

    public JiraIssueFields {
        camposCustomizados = camposCustomizados == null ? Map.of() : Map.copyOf(camposCustomizados);
    }

    public JiraIssueFields(JiraFieldRef project, String summary, Object description,
                           JiraNameRef issuetype, JiraFieldRef parent, JiraNameRef priority) {
        this(project, summary, description, issuetype, parent, priority, Map.of());
    }
}

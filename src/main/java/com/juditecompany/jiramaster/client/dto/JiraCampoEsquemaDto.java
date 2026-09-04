package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code type} distingue como o Jira espera o valor na escrita: {@code "string"} aceita texto
 * solto; {@code "option"} (Select List de escolha unica) e {@code "array"} (multi-select, labels)
 * exigem objeto/lista de objeto em vez de string — ver {@code JiraCardServiceImpl.valorParaCampo}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraCampoEsquemaDto(String type) {
}

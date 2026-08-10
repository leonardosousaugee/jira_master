package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Entrada de {@code GET /field}: o catalogo de campos da instancia, usado para achar id por nome. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraCampoDto(String id, String name) {
}

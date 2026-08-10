package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.NotBlank;

/** {@code worker} e opcional: nulo deixa o campo Worker do card em branco. */
public record CriarCardRequest(@NotBlank String titulo, String descricao, String tipoIssue, String projectKey,
                               String worker) {
}

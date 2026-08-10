package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.NotBlank;

/** {@code worker} e opcional: nulo deixa o campo Worker do sub-card em branco. */
public record AdicionarSubCardRequest(@NotBlank String titulo, String descricao, String worker) {
}

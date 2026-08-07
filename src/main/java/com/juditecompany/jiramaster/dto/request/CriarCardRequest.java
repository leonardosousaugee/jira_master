package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CriarCardRequest(@NotBlank String titulo, String descricao, String tipoIssue, String projectKey) {
}

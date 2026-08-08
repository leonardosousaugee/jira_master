package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdicionarSubCardRequest(@NotBlank String titulo, String descricao) {
}

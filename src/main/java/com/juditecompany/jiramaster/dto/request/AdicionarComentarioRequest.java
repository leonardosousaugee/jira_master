package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdicionarComentarioRequest(@NotBlank String comentario) {
}

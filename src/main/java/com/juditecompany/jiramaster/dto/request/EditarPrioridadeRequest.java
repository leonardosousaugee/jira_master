package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.NotBlank;

public record EditarPrioridadeRequest(@NotBlank String prioridade) {
}

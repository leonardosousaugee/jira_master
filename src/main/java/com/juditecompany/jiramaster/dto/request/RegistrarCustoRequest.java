package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Os quatro contadores vem separados de proposito. Quem chama nao soma e nao converte para dolar —
 * o servico nao mede nada, mas e ele que sabe as tarifas.
 */
public record RegistrarCustoRequest(
        @NotBlank String modelo,
        @PositiveOrZero long inputTokens,
        @PositiveOrZero long cacheCreationTokens,
        @PositiveOrZero long cacheReadTokens,
        @PositiveOrZero long outputTokens
) {
}

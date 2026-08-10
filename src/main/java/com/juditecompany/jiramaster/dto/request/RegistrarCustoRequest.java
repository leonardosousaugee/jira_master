package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Os contadores vem separados de proposito. Quem chama nao soma e nao converte para dolar — o
 * servico nao mede nada, mas e ele que sabe as tarifas.
 *
 * <p>{@code cacheCreation1hTokens} e opcional e vale zero por omissao: cache escrito com validade
 * de uma hora custa o dobro do de cinco minutos, entao os dois nao podem cair no mesmo contador.
 */
public record RegistrarCustoRequest(
        @NotBlank String modelo,
        @PositiveOrZero long inputTokens,
        @PositiveOrZero long cacheCreationTokens,
        @PositiveOrZero long cacheCreation1hTokens,
        @PositiveOrZero long cacheReadTokens,
        @PositiveOrZero long outputTokens
) {
}

package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Identifica a linha a estornar. O estorno nao apaga nada: acrescenta uma linha com os valores
 * negativos, para o total voltar ao certo sem que a auditoria perca o registro do erro.
 */
public record EstornarCustoRequest(@NotBlank String ts, @NotBlank String card, String motivo) {
}

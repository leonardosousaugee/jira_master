package com.juditecompany.jiramaster.dto.response;

import java.math.BigDecimal;

/**
 * {@code tabelaVersao} nao e enfeite: sem ela, uma mudanca de preco reescreve retroativamente o
 * significado de todo numero ja calculado. O resultado precisa dizer com que insumo foi produzido.
 *
 * <p>{@code detalhe} fecha exatamente com {@code custoUsd} — o total e a soma das parcelas.
 */
public record CustoCalculadoResponse(
        String modelo,
        BigDecimal custoUsd,
        Detalhe detalhe,
        String tabelaVersao
) {

    public record Detalhe(
            BigDecimal entradaNova,
            BigDecimal entradaCacheLida,
            BigDecimal entradaCacheEscrita5m,
            BigDecimal entradaCacheEscrita1h,
            BigDecimal saida
    ) {
    }
}

package com.juditecompany.jiramaster.exception;

/**
 * Nenhum contador informado nao vale zero: zero medido e "nao medido" sao coisas diferentes, e
 * devolver {@code custoUsd: 0} para quem nao mandou nada mente para baixo no numero que autoriza
 * gasto.
 */
public class ContadoresAusentesException extends RuntimeException {

    public ContadoresAusentesException() {
        super("Informe ao menos um contador de token: entradaNova, entradaCacheLida, "
                + "entradaCacheEscrita5m, entradaCacheEscrita1h ou saida. "
                + "Nenhum contador nao e o mesmo que custo zero.");
    }
}

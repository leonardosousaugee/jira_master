package com.juditecompany.jiramaster.custo;

/**
 * Os cinco contadores de token de uma execucao, separados porque cada um tem multiplicador
 * proprio sobre a tarifa de entrada — 1x, ~0,1x, 1,25x e 2x — e a saida tem tarifa propria.
 * Contador ausente vale zero; todos em zero e outra coisa e quem chama decide o que fazer.
 */
public record Contadores(
        long entradaNova,
        long entradaCacheLida,
        long entradaCacheEscrita5m,
        long entradaCacheEscrita1h,
        long saida
) {

    public boolean tudoZerado() {
        return entradaNova == 0 && entradaCacheLida == 0
                && entradaCacheEscrita5m == 0 && entradaCacheEscrita1h == 0 && saida == 0;
    }

    public long entradaTotal() {
        return entradaNova + entradaCacheLida + entradaCacheEscrita5m + entradaCacheEscrita1h;
    }
}

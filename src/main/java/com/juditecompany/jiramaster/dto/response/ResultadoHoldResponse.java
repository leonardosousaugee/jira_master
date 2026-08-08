package com.juditecompany.jiramaster.dto.response;

import java.util.List;

/**
 * Resultado do kill switch. Falha parcial e reportada, nunca engolida nem revertida em silencio:
 * este endpoint tem que ser util justamente quando as coisas ja estao dando errado, e nessa hora
 * saber quais cards pararam e quais nao vale mais do que uma resposta binaria.
 */
public record ResultadoHoldResponse(List<CardMovido> resultados) {

    public record CardMovido(String issueKey, boolean movido, String observacao) {
    }
}

package com.juditecompany.jiramaster.exception;

public class LinhaDeCustoNaoEncontradaException extends RuntimeException {

    public LinhaDeCustoNaoEncontradaException(String ts, String card, String portador) {
        super("Nenhuma linha de custo com ts \"" + ts + "\" e card \"" + card + "\" no bloco de " + portador);
    }
}

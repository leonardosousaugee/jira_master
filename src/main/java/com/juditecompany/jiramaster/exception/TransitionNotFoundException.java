package com.juditecompany.jiramaster.exception;

import java.util.List;

public class TransitionNotFoundException extends RuntimeException {

    private final List<String> transicoesDisponiveis;

    public TransitionNotFoundException(String issueKey, String etapaDestino, List<String> transicoesDisponiveis) {
        super("Etapa \"" + etapaDestino + "\" nao e uma transicao valida para o card " + issueKey
                + ". Transicoes disponiveis: " + transicoesDisponiveis);
        this.transicoesDisponiveis = transicoesDisponiveis;
    }

    public List<String> getTransicoesDisponiveis() {
        return transicoesDisponiveis;
    }
}

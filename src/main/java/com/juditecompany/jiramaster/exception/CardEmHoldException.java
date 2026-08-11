package com.juditecompany.jiramaster.exception;

/**
 * Lancada quando se tenta mover um card cuja etapa de origem e HOLD. HOLD e kill switch: a saida
 * nao passa por esta API, e sim pela mao de quem parou o trabalho.
 */
public class CardEmHoldException extends RuntimeException {

    private final String etapaAtual;

    public CardEmHoldException(String issueKey, String etapaAtual, String etapaDestino) {
        super("O card " + issueKey + " esta em \"" + etapaAtual + "\" e nao pode ser movido para \""
                + etapaDestino + "\". HOLD so e liberado manualmente, no board do Jira.");
        this.etapaAtual = etapaAtual;
    }

    public String getEtapaAtual() {
        return etapaAtual;
    }
}

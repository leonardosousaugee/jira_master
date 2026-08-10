package com.juditecompany.jiramaster.exception;

/**
 * Gravar um worker numa instancia que nao tem o campo falha em vez de seguir em silencio: um card
 * criado sem o dono que o chamador pediu e pior do que uma criacao recusada, porque a perda so
 * aparece depois, quando ninguem sabe mais de quem era o trabalho.
 */
public class CampoWorkerNaoDisponivelException extends RuntimeException {

    public CampoWorkerNaoDisponivelException() {
        super("O campo Worker nao existe nesta instancia do Jira ou nao pode ser resolvido. "
                + "Configure jira.worker-field-id (JIRA_WORKER_FIELD_ID) ou omita o campo worker na requisicao.");
    }
}

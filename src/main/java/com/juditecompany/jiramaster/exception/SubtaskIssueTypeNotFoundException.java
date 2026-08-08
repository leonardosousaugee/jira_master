package com.juditecompany.jiramaster.exception;

import java.util.List;

public class SubtaskIssueTypeNotFoundException extends RuntimeException {

    private final List<String> tiposDisponiveis;

    public SubtaskIssueTypeNotFoundException(String projectKey, List<String> tiposDisponiveis) {
        super("Projeto " + projectKey + " nao possui nenhum tipo de item marcado como subtarefa");
        this.tiposDisponiveis = tiposDisponiveis;
    }

    public List<String> getTiposDisponiveis() {
        return tiposDisponiveis;
    }
}

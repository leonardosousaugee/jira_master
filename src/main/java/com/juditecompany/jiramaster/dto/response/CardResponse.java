package com.juditecompany.jiramaster.dto.response;

import java.time.Instant;

public record CardResponse(
        String issueKey,
        String titulo,
        String descricao,
        String status,
        String prioridade,
        String tipoIssue,
        String projectKey,
        Instant criadoEm,
        Instant atualizadoEm
) {
}

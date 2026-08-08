package com.juditecompany.jiramaster.dto.response;

import com.juditecompany.jiramaster.ledger.LinhaCusto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * {@code descricao} traz so o texto humano — o bloco de custo sai dela e vem estruturado em
 * {@code custos}. {@code custoTotal} e somado na leitura e e nulo quando nao ha nenhuma linha:
 * zero e "nao medido" sao coisas diferentes, e mentir para baixo no numero que autoriza gasto e o
 * pior erro possivel aqui.
 */
public record CardResponse(
        String issueKey,
        String titulo,
        String descricao,
        String status,
        String prioridade,
        String tipoIssue,
        String projectKey,
        Instant criadoEm,
        Instant atualizadoEm,
        BigDecimal custoTotal,
        List<LinhaCusto> custos,
        int linhasDescartadas
) {
}

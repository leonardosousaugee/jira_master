package com.juditecompany.jiramaster.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Custo da arvore, sempre somado na leitura — nenhum total e gravado em lugar nenhum.
 *
 * <p>{@code filhosSemCusto} nao e detalhe: um filho sem custo registrado NAO vale zero na soma.
 * Zero e "nao medido" sao afirmacoes diferentes, e mentir para baixo no numero que autoriza
 * continuar gastando e o pior erro possivel aqui. Quem le precisa saber o quanto confiar no total.
 */
public record CustoArvoreResponse(
        String issueKey,
        BigDecimal custoProprio,
        List<CustoPorCard> porCard,
        BigDecimal custoTotal,
        List<String> filhosSemCusto,
        int linhasDescartadas
) {
    public record CustoPorCard(String issueKey, BigDecimal custo, int execucoes) {
    }
}

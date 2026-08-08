package com.juditecompany.jiramaster.ledger;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Uma execucao no ledger. Os nomes dos campos sao curtos de proposito: a linha e gravada
 * literalmente na descricao do card e lida por quebra de string.
 *
 * <p>{@code in} e a soma dos tres contadores do lado de entrada (input, cache write, cache read) —
 * volume lido, nao custo. O custo esta em {@code usd} e so nele, porque cache read custa um decimo
 * do input cheio e somar os contadores numa tarifa so erra por multiplos.
 *
 * <p>{@code estorna} so aparece em linha de estorno, apontando o {@code ts} da linha corrigida.
 * Linhas normais nao carregam o campo — dai o NON_NULL, para nao poluir todas as linhas do bloco
 * com um {@code null} que nao diz nada.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinhaCusto(String ts, String card, long in, long out, BigDecimal usd, String estorna) {

    public LinhaCusto(String ts, String card, long in, long out, BigDecimal usd) {
        this(ts, card, in, out, usd, null);
    }

    public boolean ehEstorno() {
        return estorna != null;
    }
}

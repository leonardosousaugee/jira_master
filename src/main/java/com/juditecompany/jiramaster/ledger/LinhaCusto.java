package com.juditecompany.jiramaster.ledger;

import java.math.BigDecimal;

/**
 * Uma execucao no ledger. Os nomes dos campos sao curtos de proposito: a linha e gravada
 * literalmente na descricao do card e lida por quebra de string.
 *
 * <p>{@code in} e a soma dos tres contadores do lado de entrada (input, cache write, cache read) —
 * volume lido, nao custo. O custo esta em {@code usd} e so nele, porque cache read custa um decimo
 * do input cheio e somar os contadores numa tarifa so erra por multiplos.
 */
public record LinhaCusto(String ts, String card, long in, long out, BigDecimal usd) {
}

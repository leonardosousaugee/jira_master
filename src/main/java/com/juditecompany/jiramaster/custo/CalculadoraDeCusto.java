package com.juditecompany.jiramaster.custo;

import com.juditecompany.jiramaster.config.TarifaProperties;
import com.juditecompany.jiramaster.exception.ModeloDesconhecidoException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A unica tabela de tarifas em codigo, com dois chamadores: o endpoint de consulta e o registro de
 * custo no card. Duas calculadoras seria o problema de hoje — a tabela copiada — com passos extras.
 *
 * <p>O total e a soma das parcelas ja arredondadas, e nao o arredondamento da soma: assim o detalhe
 * fecha exatamente com o total, e uma linha de ledger que nao fecha com as proprias parcelas nao e
 * auditavel.
 */
@Component
public class CalculadoraDeCusto {

    private static final BigDecimal POR_MILHAO = BigDecimal.valueOf(1_000_000);
    private static final int CASAS = 4;

    private final TarifaProperties tarifaProperties;

    public CalculadoraDeCusto(TarifaProperties tarifaProperties) {
        this.tarifaProperties = tarifaProperties;
    }

    public record Detalhe(
            BigDecimal entradaNova,
            BigDecimal entradaCacheLida,
            BigDecimal entradaCacheEscrita5m,
            BigDecimal entradaCacheEscrita1h,
            BigDecimal saida
    ) {
        public BigDecimal soma() {
            return entradaNova.add(entradaCacheLida).add(entradaCacheEscrita5m)
                    .add(entradaCacheEscrita1h).add(saida);
        }
    }

    public record Resultado(String modelo, BigDecimal custoUsd, Detalhe detalhe, String tabelaVersao) {
    }

    /**
     * Modelo desconhecido e recusado, nunca calculado com uma tarifa padrao: numero errado com cara
     * de certo e o pior resultado possivel para um ledger.
     */
    public Resultado calcular(String modelo, Contadores contadores) {
        TarifaProperties.Tarifa tarifa = tarifaProperties.getTarifas().get(modelo);
        if (tarifa == null) {
            throw new ModeloDesconhecidoException(modelo, tarifaProperties.modelosConhecidos());
        }

        var detalhe = new Detalhe(
                parcela(contadores.entradaNova(), tarifa.input()),
                parcela(contadores.entradaCacheLida(), tarifa.cacheRead()),
                parcela(contadores.entradaCacheEscrita5m(), tarifa.cacheWrite5m()),
                parcela(contadores.entradaCacheEscrita1h(), tarifa.cacheWrite1h()),
                parcela(contadores.saida(), tarifa.output()));

        return new Resultado(modelo, detalhe.soma(), detalhe, tarifaProperties.getTabelaVersao());
    }

    private BigDecimal parcela(long tokens, BigDecimal tarifaPorMilhao) {
        return tarifaPorMilhao.multiply(BigDecimal.valueOf(tokens))
                .divide(POR_MILHAO, CASAS, RoundingMode.HALF_UP);
    }
}

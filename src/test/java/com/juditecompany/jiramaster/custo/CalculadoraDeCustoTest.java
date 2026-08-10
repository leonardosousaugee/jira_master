package com.juditecompany.jiramaster.custo;

import com.juditecompany.jiramaster.config.TarifaProperties;
import com.juditecompany.jiramaster.exception.ModeloDesconhecidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraDeCustoTest {

    private final CalculadoraDeCusto calculadora = new CalculadoraDeCusto(tarifasDeTeste());

    private TarifaProperties tarifasDeTeste() {
        TarifaProperties tarifas = new TarifaProperties();
        tarifas.setTabelaVersao("2026-06-24");
        tarifas.setTarifas(new LinkedHashMap<>(Map.of("claude-opus-5",
                new TarifaProperties.Tarifa(new BigDecimal("5.00"), new BigDecimal("25.00"),
                        new BigDecimal("6.25"), new BigDecimal("10.00"), new BigDecimal("0.50")))));
        return tarifas;
    }

    @Test
    void deveCobrarCadaContadorPelaSuaTarifa() {
        var resultado = calculadora.calcular("claude-opus-5",
                new Contadores(1000, 1_000_000, 1000, 0, 1000));

        assertThat(resultado.detalhe().entradaNova()).isEqualByComparingTo("0.0050");
        assertThat(resultado.detalhe().entradaCacheLida()).isEqualByComparingTo("0.5000");
        assertThat(resultado.detalhe().entradaCacheEscrita5m()).isEqualByComparingTo("0.0063");
        assertThat(resultado.detalhe().entradaCacheEscrita1h()).isEqualByComparingTo("0.0000");
        assertThat(resultado.detalhe().saida()).isEqualByComparingTo("0.0250");
        assertThat(resultado.custoUsd()).isEqualByComparingTo("0.5363");
        assertThat(resultado.tabelaVersao()).isEqualTo("2026-06-24");
    }

    /** O total tem que fechar com a soma do detalhe, senao a linha do ledger nao e auditavel. */
    @Test
    void deveFecharOTotalComASomaDoDetalhe() {
        var resultado = calculadora.calcular("claude-opus-5",
                new Contadores(120_000, 850_000, 0, 40_000, 6000));

        BigDecimal soma = resultado.detalhe().entradaNova()
                .add(resultado.detalhe().entradaCacheLida())
                .add(resultado.detalhe().entradaCacheEscrita5m())
                .add(resultado.detalhe().entradaCacheEscrita1h())
                .add(resultado.detalhe().saida());

        assertThat(resultado.custoUsd()).isEqualByComparingTo(soma);
    }

    @Test
    void deveCobrarCacheLidoDezVezesMaisBaratoQueEntradaNova() {
        var lido = calculadora.calcular("claude-opus-5", new Contadores(0, 1_000_000, 0, 0, 0));
        var nova = calculadora.calcular("claude-opus-5", new Contadores(1_000_000, 0, 0, 0, 0));

        assertThat(nova.custoUsd()).isEqualByComparingTo(lido.custoUsd().multiply(BigDecimal.TEN));
    }

    @Test
    void deveCobrarCacheDeUmaHoraDobradoEmRelacaoAEntradaNova() {
        var umaHora = calculadora.calcular("claude-opus-5", new Contadores(0, 0, 0, 1_000_000, 0));
        var nova = calculadora.calcular("claude-opus-5", new Contadores(1_000_000, 0, 0, 0, 0));

        assertThat(umaHora.custoUsd()).isEqualByComparingTo(nova.custoUsd().multiply(new BigDecimal("2")));
    }

    @Test
    void deveRecusarModeloDesconhecidoEmVezDeUsarTarifaPadrao() {
        assertThatThrownBy(() -> calculadora.calcular("claude-opus-5[1m]", new Contadores(1000, 0, 0, 0, 1000)))
                .isInstanceOf(ModeloDesconhecidoException.class)
                .hasMessageContaining("claude-opus-5[1m]");
    }
}

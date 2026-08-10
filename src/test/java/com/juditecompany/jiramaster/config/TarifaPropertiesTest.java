package com.juditecompany.jiramaster.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TarifaPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    /**
     * A tabela real vive no application.yml e so quebraria em runtime se um nome de propriedade nao
     * casasse com o componente do record — este teste faz a ligacao falhar em teste, nao em produca.
     */
    @Test
    void deveVincularOsCincoPrecosEAVersaoDaTabela() {
        contextRunner
                .withPropertyValues(
                        "custo.tabela-versao=2026-06-24",
                        "custo.tarifas.claude-opus-5.input=5.00",
                        "custo.tarifas.claude-opus-5.output=25.00",
                        "custo.tarifas.claude-opus-5.cache-write5m=6.25",
                        "custo.tarifas.claude-opus-5.cache-write1h=10.00",
                        "custo.tarifas.claude-opus-5.cache-read=0.50")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TarifaProperties props = context.getBean(TarifaProperties.class);
                    TarifaProperties.Tarifa tarifa = props.getTarifas().get("claude-opus-5");

                    assertThat(props.getTabelaVersao()).isEqualTo("2026-06-24");
                    assertThat(tarifa.input()).isEqualByComparingTo(new BigDecimal("5.00"));
                    assertThat(tarifa.output()).isEqualByComparingTo(new BigDecimal("25.00"));
                    assertThat(tarifa.cacheWrite5m()).isEqualByComparingTo(new BigDecimal("6.25"));
                    assertThat(tarifa.cacheWrite1h()).isEqualByComparingTo(new BigDecimal("10.00"));
                    assertThat(tarifa.cacheRead()).isEqualByComparingTo(new BigDecimal("0.50"));
                });
    }

    @EnableConfigurationProperties(TarifaProperties.class)
    static class TestConfig {
    }
}

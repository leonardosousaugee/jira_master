package com.juditecompany.jiramaster.controller;

import com.juditecompany.jiramaster.config.TarifaProperties;
import com.juditecompany.jiramaster.custo.CalculadoraDeCusto;
import com.juditecompany.jiramaster.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TarifaController.class)
@Import({TarifaControllerTest.TarifasDeTeste.class, GlobalExceptionHandler.class})
class TarifaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TarifasDeTeste {
        @Bean
        CalculadoraDeCusto calculadoraDeCusto() {
            TarifaProperties tarifas = new TarifaProperties();
            tarifas.setTabelaVersao("2026-06-24");
            tarifas.setTarifas(new LinkedHashMap<>(Map.of("claude-opus-5",
                    new TarifaProperties.Tarifa(new BigDecimal("5.00"), new BigDecimal("25.00"),
                            new BigDecimal("6.25"), new BigDecimal("10.00"), new BigDecimal("0.50")))));
            return new CalculadoraDeCusto(tarifas);
        }
    }

    @Test
    void deveCalcularOCustoComOsCincoContadores() throws Exception {
        mockMvc.perform(get("/api/tarifas/custo")
                        .param("modelo", "claude-opus-5")
                        .param("entradaNova", "120000")
                        .param("entradaCacheLida", "850000")
                        .param("entradaCacheEscrita5m", "0")
                        .param("entradaCacheEscrita1h", "40000")
                        .param("saida", "6000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo").value("claude-opus-5"))
                .andExpect(jsonPath("$.custoUsd").value(1.575))
                .andExpect(jsonPath("$.detalhe.entradaNova").value(0.6))
                .andExpect(jsonPath("$.detalhe.entradaCacheLida").value(0.425))
                .andExpect(jsonPath("$.detalhe.entradaCacheEscrita5m").value(0.0))
                .andExpect(jsonPath("$.detalhe.entradaCacheEscrita1h").value(0.4))
                .andExpect(jsonPath("$.detalhe.saida").value(0.15))
                .andExpect(jsonPath("$.tabelaVersao").value("2026-06-24"));
    }

    @Test
    void deveTratarContadorAusenteComoZero() throws Exception {
        mockMvc.perform(get("/api/tarifas/custo")
                        .param("modelo", "claude-opus-5")
                        .param("saida", "1000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.custoUsd").value(25.0));
    }

    /** Zero medido e "nao medido" sao coisas diferentes: sem nenhum contador o endpoint recusa. */
    @Test
    void deveRecusarRequisicaoSemNenhumContador() throws Exception {
        mockMvc.perform(get("/api/tarifas/custo").param("modelo", "claude-opus-5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRecusarModeloDesconhecidoComOsModelosConhecidos() throws Exception {
        mockMvc.perform(get("/api/tarifas/custo")
                        .param("modelo", "claude-opus-5[1m]")
                        .param("saida", "1000"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.modelosConhecidos[0]").value("claude-opus-5"));
    }
}

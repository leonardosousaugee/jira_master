package com.juditecompany.jiramaster.controller;

import com.juditecompany.jiramaster.custo.CalculadoraDeCusto;
import com.juditecompany.jiramaster.custo.Contadores;
import com.juditecompany.jiramaster.dto.response.CustoCalculadoResponse;
import com.juditecompany.jiramaster.exception.ContadoresAusentesException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calculo puro: nao grava, nao move card, nao escreve linha de custo. Registrar e o outro endpoint;
 * misturar os dois faria consulta virar escrita acidental. O caminho de registro tambem nao depende
 * deste endpoint — os dois compartilham a calculadora, entao consulta fora do ar nao impede o
 * trabalho de ser gravado.
 */
@Validated
@RestController
@RequestMapping("/api/tarifas")
@Tag(name = "Tarifas", description = "Tabela de tarifas por modelo e calculo de custo em dolar")
public class TarifaController {

    private final CalculadoraDeCusto calculadora;

    public TarifaController(CalculadoraDeCusto calculadora) {
        this.calculadora = calculadora;
    }

    @GetMapping("/custo")
    public ResponseEntity<CustoCalculadoResponse> calcularCusto(
            @RequestParam @NotBlank String modelo,
            @RequestParam(defaultValue = "0") @PositiveOrZero long entradaNova,
            @RequestParam(defaultValue = "0") @PositiveOrZero long entradaCacheLida,
            @RequestParam(defaultValue = "0") @PositiveOrZero long entradaCacheEscrita5m,
            @RequestParam(defaultValue = "0") @PositiveOrZero long entradaCacheEscrita1h,
            @RequestParam(defaultValue = "0") @PositiveOrZero long saida) {

        var contadores = new Contadores(entradaNova, entradaCacheLida,
                entradaCacheEscrita5m, entradaCacheEscrita1h, saida);
        if (contadores.tudoZerado()) {
            throw new ContadoresAusentesException();
        }

        CalculadoraDeCusto.Resultado resultado = calculadora.calcular(modelo, contadores);
        var detalhe = new CustoCalculadoResponse.Detalhe(
                resultado.detalhe().entradaNova(),
                resultado.detalhe().entradaCacheLida(),
                resultado.detalhe().entradaCacheEscrita5m(),
                resultado.detalhe().entradaCacheEscrita1h(),
                resultado.detalhe().saida());

        return ResponseEntity.ok(new CustoCalculadoResponse(
                resultado.modelo(), resultado.custoUsd(), detalhe, resultado.tabelaVersao()));
    }
}

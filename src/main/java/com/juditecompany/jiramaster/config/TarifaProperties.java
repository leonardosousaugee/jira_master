package com.juditecompany.jiramaster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tarifas por milhao de tokens, por modelo. Fica em configuracao e nao em literal no codigo porque
 * preco muda e modelo novo aparece.
 *
 * <p>Os cinco contadores tem tarifas diferentes e a diferenca nao e cosmetica: cache lido custa um
 * decimo da entrada nova e domina o volume de sessao longa, enquanto cache escrito com validade de
 * uma hora custa o dobro dela. Somar os contadores de entrada numa tarifa so erra por multiplos.
 *
 * <p>{@code tabelaVersao} sai em toda resposta de calculo. Sem ela, uma mudanca de preco reescreve
 * retroativamente o significado de todo numero ja calculado: o resultado precisa dizer com que
 * insumo foi produzido.
 */
@ConfigurationProperties(prefix = "custo")
public class TarifaProperties {

    private Map<String, Tarifa> tarifas = new LinkedHashMap<>();

    private String tabelaVersao;

    public record Tarifa(BigDecimal input, BigDecimal output,
                         BigDecimal cacheWrite5m, BigDecimal cacheWrite1h, BigDecimal cacheRead) {
    }

    public Map<String, Tarifa> getTarifas() {
        return tarifas;
    }

    public void setTarifas(Map<String, Tarifa> tarifas) {
        this.tarifas = tarifas;
    }

    public String getTabelaVersao() {
        return tabelaVersao;
    }

    public void setTabelaVersao(String tabelaVersao) {
        this.tabelaVersao = tabelaVersao;
    }

    public List<String> modelosConhecidos() {
        return List.copyOf(tarifas.keySet());
    }
}

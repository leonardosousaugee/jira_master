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
 * <p>Os quatro contadores tem tarifas diferentes e a diferenca nao e cosmetica: cache read custa um
 * decimo do input cheio e domina o volume de sessao longa. Somar os contadores de entrada numa
 * tarifa so erra por multiplos, sempre para cima.
 */
@ConfigurationProperties(prefix = "custo")
public class TarifaProperties {

    private Map<String, Tarifa> tarifas = new LinkedHashMap<>();

    public record Tarifa(BigDecimal input, BigDecimal output, BigDecimal cacheWrite, BigDecimal cacheRead) {
    }

    public Map<String, Tarifa> getTarifas() {
        return tarifas;
    }

    public void setTarifas(Map<String, Tarifa> tarifas) {
        this.tarifas = tarifas;
    }

    public List<String> modelosConhecidos() {
        return List.copyOf(tarifas.keySet());
    }
}

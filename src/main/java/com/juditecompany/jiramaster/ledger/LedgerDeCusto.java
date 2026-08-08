package com.juditecompany.jiramaster.ledger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Le e escreve o bloco de custo dentro da descricao de um card.
 *
 * <p>Tudo que estiver fora das sentinelas e texto humano e sobrevive intacto a qualquer escrita —
 * o splice troca so o miolo. O total nunca e gravado: as linhas sao, e a soma acontece na leitura.
 */
@Component
public class LedgerDeCusto {

    public static final String ABERTURA = "<!-- custo:v1 -->";
    public static final String FECHAMENTO = "<!-- /custo -->";

    private static final Logger log = LoggerFactory.getLogger(LedgerDeCusto.class);

    private final ObjectMapper objectMapper;

    public LedgerDeCusto(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record Partes(String antes, String conteudoBloco, String depois) {
        public boolean temBloco() {
            return conteudoBloco != null;
        }
    }

    public record Leitura(List<LinhaCusto> linhas, int linhasDescartadas) {
        public boolean vazio() {
            return linhas.isEmpty() && linhasDescartadas == 0;
        }

        public BigDecimal total() {
            return linhas.stream().map(LinhaCusto::usd).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /**
     * Separa a descricao em texto antes do bloco, conteudo do bloco e texto depois.
     * Abertura sem fechamento e tratada como ausencia de bloco — nao se adivinha onde termina.
     * Havendo mais de uma abertura, vale a primeira.
     */
    public Partes separar(String descricao) {
        String texto = descricao != null ? descricao : "";

        int inicioAbertura = texto.indexOf(ABERTURA);
        if (inicioAbertura < 0) {
            return new Partes(texto, null, "");
        }

        int inicioFechamento = texto.indexOf(FECHAMENTO, inicioAbertura + ABERTURA.length());
        if (inicioFechamento < 0) {
            log.warn("Bloco de custo com abertura sem fechamento; tratando como ausente");
            return new Partes(texto, null, "");
        }

        String conteudo = texto.substring(inicioAbertura + ABERTURA.length(), inicioFechamento);
        String depois = texto.substring(inicioFechamento + FECHAMENTO.length());
        return new Partes(texto.substring(0, inicioAbertura), conteudo.strip(), depois.strip());
    }

    /**
     * Linha malformada e ignorada e contada, nunca derruba a leitura — um caractere torto num card
     * nao pode cegar o custo da arvore inteira.
     */
    public Leitura ler(String descricao) {
        Partes partes = separar(descricao);
        if (!partes.temBloco()) {
            return new Leitura(List.of(), 0);
        }

        List<LinhaCusto> linhas = new ArrayList<>();
        int descartadas = 0;
        for (String linha : partes.conteudoBloco().split("\n")) {
            String limpa = linha.strip();
            if (limpa.isEmpty()) {
                continue;
            }
            try {
                linhas.add(objectMapper.readValue(limpa, LinhaCusto.class));
            } catch (JsonProcessingException ex) {
                descartadas++;
                log.warn("Linha de custo descartada por formato invalido: {}", ex.getOriginalMessage());
            }
        }
        return new Leitura(List.copyOf(linhas), descartadas);
    }

    /**
     * Devolve a descricao com a linha acrescentada, preservando byte a byte tudo fora das
     * sentinelas. Sem bloco, cria um no fim.
     */
    public String acrescentar(String descricao, LinhaCusto novaLinha) {
        Partes partes = separar(descricao);

        String conteudo = partes.temBloco() && !partes.conteudoBloco().isEmpty()
                ? partes.conteudoBloco() + "\n" + serializar(novaLinha)
                : serializar(novaLinha);

        String bloco = ABERTURA + "\n" + conteudo + "\n" + FECHAMENTO;

        if (!partes.temBloco()) {
            String antes = partes.antes();
            return antes.isEmpty() ? bloco : antes.stripTrailing() + "\n\n" + bloco;
        }
        return partes.antes() + bloco + (partes.depois().isEmpty() ? "" : "\n" + partes.depois());
    }

    private String serializar(LinhaCusto linha) {
        try {
            return objectMapper.writeValueAsString(linha);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar linha de custo", ex);
        }
    }
}

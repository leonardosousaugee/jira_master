package com.juditecompany.jiramaster.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AdfMapper {

    public Map<String, Object> textoParaAdf(String texto) {
        Map<String, Object> documento = new LinkedHashMap<>();
        documento.put("type", "doc");
        documento.put("version", 1);

        if (texto == null || texto.isBlank()) {
            documento.put("content", List.of());
            return documento;
        }

        Map<String, Object> textNode = new LinkedHashMap<>();
        textNode.put("type", "text");
        textNode.put("text", texto);

        Map<String, Object> paragraph = new LinkedHashMap<>();
        paragraph.put("type", "paragraph");
        paragraph.put("content", List.of(textNode));

        documento.put("content", List.of(paragraph));
        return documento;
    }

    /**
     * Monta um documento com varios nos. Cada linha do texto humano vira um paragrafo proprio —
     * ADF nao renderiza \n dentro de um no de texto, e um paragrafo com varias linhas colapsaria
     * em uma so na tela. Linha em branco vira paragrafo de conteudo vazio, que o adfParaTexto
     * devolve como quebra, mantendo o round-trip fiel.
     */
    public Map<String, Object> documento(List<Map<String, Object>> nos) {
        Map<String, Object> documento = new LinkedHashMap<>();
        documento.put("type", "doc");
        documento.put("version", 1);
        documento.put("content", nos);
        return documento;
    }

    public List<Map<String, Object>> paragrafosDeTexto(String texto) {
        if (texto == null || texto.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(texto.split("\n", -1)).map(this::paragrafo).toList();
    }

    public Map<String, Object> paragrafo(String linha) {
        Map<String, Object> paragrafo = new LinkedHashMap<>();
        paragrafo.put("type", "paragraph");
        paragrafo.put("content", linha.isEmpty() ? List.of() : List.of(no("text", linha)));
        return paragrafo;
    }

    /**
     * Bloco de codigo preserva as quebras de linha e renderiza monoespacado, que e o unico jeito
     * de um bloco multi-linha continuar legivel na tela do Jira.
     */
    public Map<String, Object> blocoDeCodigo(String conteudo) {
        Map<String, Object> bloco = new LinkedHashMap<>();
        bloco.put("type", "codeBlock");
        bloco.put("content", List.of(no("text", conteudo)));
        return bloco;
    }

    private Map<String, Object> no(String tipo, String texto) {
        Map<String, Object> no = new LinkedHashMap<>();
        no.put("type", tipo);
        no.put("text", texto);
        return no;
    }

    public String adfParaTexto(JsonNode adf) {
        if (adf == null || adf.isNull()) {
            return "";
        }
        StringBuilder texto = new StringBuilder();
        coletarTexto(adf, texto);
        return texto.toString().strip();
    }

    private void coletarTexto(JsonNode node, StringBuilder acumulador) {
        if (node.has("text")) {
            acumulador.append(node.get("text").asText());
        }
        if (node.has("content")) {
            for (JsonNode filho : node.get("content")) {
                coletarTexto(filho, acumulador);
            }
            acumulador.append("\n");
        }
    }
}

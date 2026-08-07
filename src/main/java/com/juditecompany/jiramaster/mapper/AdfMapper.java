package com.juditecompany.jiramaster.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

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

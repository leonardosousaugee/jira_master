package com.juditecompany.jiramaster.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdfMapperTest {

    private final AdfMapper mapper = new AdfMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deveEmbrulharTextoSimplesEmDocumentoAdf() {
        Map<String, Object> adf = mapper.textoParaAdf("Corrigir bug no login");

        assertThat(adf.get("type")).isEqualTo("doc");
        assertThat(adf.get("version")).isEqualTo(1);
    }

    @Test
    void deveExtrairTextoSimplesDeUmDocumentoAdf() throws Exception {
        String json = """
                {
                  "type": "doc",
                  "version": 1,
                  "content": [
                    { "type": "paragraph", "content": [ { "type": "text", "text": "Corrigir bug no login" } ] }
                  ]
                }
                """;
        JsonNode adf = objectMapper.readTree(json);

        String texto = mapper.adfParaTexto(adf);

        assertThat(texto).isEqualTo("Corrigir bug no login");
    }

    @Test
    void deveRetornarStringVaziaQuandoAdfForNulo() {
        assertThat(mapper.adfParaTexto(null)).isEmpty();
    }

    @Test
    void deveRetornarDocumentoAdfVazioParaTextoNulo() {
        Map<String, Object> adf = mapper.textoParaAdf(null);

        assertThat(adf.get("type")).isEqualTo("doc");
        assertThat(adf.get("version")).isEqualTo(1);
        assertThat(adf.get("content")).isEqualTo(List.of());
    }

    @Test
    void deveRetornarDocumentoAdfVazioParaTextoEmBranco() {
        Map<String, Object> adf = mapper.textoParaAdf("");

        assertThat(adf.get("type")).isEqualTo("doc");
        assertThat(adf.get("version")).isEqualTo(1);
        assertThat(adf.get("content")).isEqualTo(List.of());
    }

    @Test
    void deveMontarDocumentoComParagrafoEBlocoDeCodigo() {
        Map<String, Object> doc = mapper.documento(List.of(
                mapper.paragrafo("Texto humano"),
                mapper.blocoDeCodigo("linha1\nlinha2")));

        JsonNode node = objectMapper.valueToTree(doc);

        assertThat(node.get("content").get(0).get("type").asText()).isEqualTo("paragraph");
        assertThat(node.get("content").get(1).get("type").asText()).isEqualTo("codeBlock");
        assertThat(mapper.adfParaTexto(node)).isEqualTo("Texto humano\nlinha1\nlinha2");
    }

    @Test
    void deveFazerRoundTripDeLinhaEmBrancoNoTextoHumano() {
        Map<String, Object> doc = mapper.documento(mapper.paragrafosDeTexto("a\n\nb"));

        JsonNode node = objectMapper.valueToTree(doc);

        assertThat(mapper.adfParaTexto(node)).isEqualTo("a\n\nb");
    }
}

package com.juditecompany.jiramaster.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juditecompany.jiramaster.client.dto.*;
import com.juditecompany.jiramaster.dto.response.CardResponse;
import com.juditecompany.jiramaster.dto.response.ComentarioResponse;
import com.juditecompany.jiramaster.dto.response.TransicaoResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JiraCardMapperTest {

    private final JiraCardMapper mapper = new JiraCardMapper(new AdfMapper());
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deveMapearIssueParaCardResponse() throws Exception {
        var descricao = objectMapper.readTree("""
                {"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"Descricao"}]}]}
                """);
        var fields = new JiraIssueResponseFields(
                "Titulo", descricao, new JiraStatusDto("To Do"), new JiraNameRef("Medium"),
                new JiraNameRef("Task"), new JiraFieldRef("KAN"),
                "2026-08-05T10:00:00.000+0000", "2026-08-05T11:00:00.000+0000");
        var issue = new JiraIssueDto("10001", "KAN-1", fields);

        CardResponse resultado = mapper.paraCardResponse(issue);

        assertThat(resultado.issueKey()).isEqualTo("KAN-1");
        assertThat(resultado.titulo()).isEqualTo("Titulo");
        assertThat(resultado.descricao()).isEqualTo("Descricao");
        assertThat(resultado.status()).isEqualTo("To Do");
        assertThat(resultado.prioridade()).isEqualTo("Medium");
        assertThat(resultado.tipoIssue()).isEqualTo("Task");
        assertThat(resultado.projectKey()).isEqualTo("KAN");
        assertThat(resultado.criadoEm()).isEqualTo(Instant.parse("2026-08-05T10:00:00Z"));
    }

    @Test
    void deveMapearTransicaoParaTransicaoResponse() {
        var transicao = new JiraTransitionDto("31", "Start Progress", new JiraTransitionToDto("In Progress"));

        TransicaoResponse resultado = mapper.paraTransicaoResponse(transicao);

        assertThat(resultado.id()).isEqualTo("31");
        assertThat(resultado.nomeEtapaDestino()).isEqualTo("In Progress");
    }

    @Test
    void deveMapearComentarioParaComentarioResponse() throws Exception {
        var corpo = objectMapper.readTree("""
                {"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"Comentario"}]}]}
                """);
        var comentario = new JiraCommentDto("10050", new JiraCommentAuthorDto("Leonardo"), corpo, "2026-08-05T10:00:00.000+0000");

        ComentarioResponse resultado = mapper.paraComentarioResponse(comentario);

        assertThat(resultado.autor()).isEqualTo("Leonardo");
        assertThat(resultado.corpo()).isEqualTo("Comentario");
    }
}

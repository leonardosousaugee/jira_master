package com.juditecompany.jiramaster.mapper;

import com.juditecompany.jiramaster.client.dto.JiraCommentDto;
import com.juditecompany.jiramaster.client.dto.JiraIssueDto;
import com.juditecompany.jiramaster.client.dto.JiraTransitionDto;
import com.juditecompany.jiramaster.dto.response.CardResponse;
import com.juditecompany.jiramaster.dto.response.CardResumoResponse;
import com.juditecompany.jiramaster.dto.response.ComentarioResponse;
import com.juditecompany.jiramaster.dto.response.TransicaoResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class JiraCardMapper {

    // Jira devolve o offset sem separador de dois-pontos (ex.: "+0000"), formato
    // que o parser ISO padrao do java.time rejeita — por isso o padrao customizado.
    private static final DateTimeFormatter FORMATO_DATA_JIRA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private final AdfMapper adfMapper;

    public JiraCardMapper(AdfMapper adfMapper) {
        this.adfMapper = adfMapper;
    }

    public CardResponse paraCardResponse(JiraIssueDto issue) {
        var fields = issue.fields();
        return new CardResponse(
                issue.key(),
                fields.summary(),
                adfMapper.adfParaTexto(fields.description()),
                fields.status().name(),
                fields.priority() != null ? fields.priority().name() : null,
                fields.issuetype().name(),
                fields.project().key(),
                paraInstant(fields.created()),
                paraInstant(fields.updated())
        );
    }

    public CardResumoResponse paraCardResumoResponse(JiraIssueDto issue) {
        var fields = issue.fields();
        return new CardResumoResponse(
                issue.key(),
                fields.summary(),
                fields.status().name(),
                fields.priority() != null ? fields.priority().name() : null
        );
    }

    public TransicaoResponse paraTransicaoResponse(JiraTransitionDto transicao) {
        return new TransicaoResponse(transicao.id(), transicao.to().name());
    }

    public ComentarioResponse paraComentarioResponse(JiraCommentDto comentario) {
        return new ComentarioResponse(
                comentario.id(),
                comentario.author().displayName(),
                adfMapper.adfParaTexto(comentario.body()),
                paraInstant(comentario.created())
        );
    }

    private Instant paraInstant(String dataJira) {
        if (dataJira == null) {
            return null;
        }
        return OffsetDateTime.parse(dataJira, FORMATO_DATA_JIRA).toInstant();
    }
}

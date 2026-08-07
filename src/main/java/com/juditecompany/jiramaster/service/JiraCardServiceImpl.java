package com.juditecompany.jiramaster.service;

import com.juditecompany.jiramaster.client.JiraApiClient;
import com.juditecompany.jiramaster.client.dto.*;
import com.juditecompany.jiramaster.config.JiraProperties;
import com.juditecompany.jiramaster.dto.request.*;
import com.juditecompany.jiramaster.dto.response.*;
import com.juditecompany.jiramaster.exception.CardNotFoundException;
import com.juditecompany.jiramaster.exception.JiraApiException;
import com.juditecompany.jiramaster.exception.TransitionNotFoundException;
import com.juditecompany.jiramaster.mapper.AdfMapper;
import com.juditecompany.jiramaster.mapper.JiraCardMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JiraCardServiceImpl implements JiraCardService {

    private final JiraApiClient jiraApiClient;
    private final JiraCardMapper cardMapper;
    private final AdfMapper adfMapper;
    private final JiraProperties jiraProperties;

    public JiraCardServiceImpl(JiraApiClient jiraApiClient, JiraCardMapper cardMapper,
                                AdfMapper adfMapper, JiraProperties jiraProperties) {
        this.jiraApiClient = jiraApiClient;
        this.cardMapper = cardMapper;
        this.adfMapper = adfMapper;
        this.jiraProperties = jiraProperties;
    }

    @Override
    public CardResponse criarCard(CriarCardRequest request) {
        String projectKey = request.projectKey() != null ? request.projectKey() : jiraProperties.getDefaultProjectKey();
        String tipoIssue = request.tipoIssue() != null ? request.tipoIssue() : "Task";
        Object descricaoAdf = request.descricao() != null ? adfMapper.textoParaAdf(request.descricao()) : null;

        var fields = new JiraIssueFields(new JiraFieldRef(projectKey), request.titulo(), descricaoAdf,
                new JiraNameRef(tipoIssue), null, null);
        JiraCreatedIssueDto criado = jiraApiClient.criarIssue(new JiraIssueRequest(fields));

        return cardMapper.paraCardResponse(jiraApiClient.buscarIssuePorChave(criado.key()));
    }

    @Override
    public List<CardResumoResponse> lerCardsEmAberto(String projectKeyOverride) {
        String projectKey = projectKeyOverride != null ? projectKeyOverride : jiraProperties.getDefaultProjectKey();
        String jql = "project = " + projectKey + " AND statusCategory != Done ORDER BY created DESC";

        return jiraApiClient.buscarIssues(jql).issues().stream()
                .map(cardMapper::paraCardResumoResponse)
                .toList();
    }

    @Override
    public CardResponse buscarCardPorId(String issueKey) {
        return cardMapper.paraCardResponse(buscarIssueOuLancarNaoEncontrado(issueKey));
    }

    @Override
    public CardResponse editarCard(String issueKey, EditarCardRequest request) {
        Object descricaoAdf = request.descricao() != null ? adfMapper.textoParaAdf(request.descricao()) : null;
        var fields = new JiraIssueFields(null, request.titulo(), descricaoAdf, null, null, null);

        atualizarIssueOuLancarNaoEncontrado(issueKey, fields);

        return cardMapper.paraCardResponse(buscarIssueOuLancarNaoEncontrado(issueKey));
    }

    @Override
    public List<TransicaoResponse> listarTransicoesDisponiveis(String issueKey) {
        return jiraApiClient.buscarTransicoes(issueKey).transitions().stream()
                .map(cardMapper::paraTransicaoResponse)
                .toList();
    }

    @Override
    public void alterarEtapaCard(String issueKey, AlterarEtapaRequest request) {
        List<JiraTransitionDto> transicoes = jiraApiClient.buscarTransicoes(issueKey).transitions();

        JiraTransitionDto transicaoEncontrada = transicoes.stream()
                .filter(t -> t.to().name().equalsIgnoreCase(request.etapaDestino()))
                .findFirst()
                .orElseThrow(() -> new TransitionNotFoundException(
                        issueKey, request.etapaDestino(),
                        transicoes.stream().map(t -> t.to().name()).toList()));

        jiraApiClient.executarTransicao(issueKey, transicaoEncontrada.id());
    }

    @Override
    public CardResponse adicionarSubCard(String issueKeyPai, AdicionarSubCardRequest request) {
        Object descricaoAdf = request.descricao() != null ? adfMapper.textoParaAdf(request.descricao()) : null;
        var fields = new JiraIssueFields(new JiraFieldRef(jiraProperties.getDefaultProjectKey()),
                request.titulo(), descricaoAdf, new JiraNameRef("Subtask"), new JiraFieldRef(issueKeyPai), null);

        JiraCreatedIssueDto criado = jiraApiClient.criarIssue(new JiraIssueRequest(fields));

        return cardMapper.paraCardResponse(jiraApiClient.buscarIssuePorChave(criado.key()));
    }

    @Override
    public void editarPrioridade(String issueKey, EditarPrioridadeRequest request) {
        var fields = new JiraIssueFields(null, null, null, null, null, new JiraNameRef(request.prioridade()));
        atualizarIssueOuLancarNaoEncontrado(issueKey, fields);
    }

    @Override
    public ComentarioResponse adicionarComentario(String issueKey, AdicionarComentarioRequest request) {
        JiraCommentDto criado = jiraApiClient.adicionarComentario(issueKey, adfMapper.textoParaAdf(request.comentario()));
        return cardMapper.paraComentarioResponse(criado);
    }

    private JiraIssueDto buscarIssueOuLancarNaoEncontrado(String issueKey) {
        try {
            return jiraApiClient.buscarIssuePorChave(issueKey);
        } catch (JiraApiException ex) {
            if (ex.getStatus().equals(HttpStatus.NOT_FOUND)) {
                throw new CardNotFoundException(issueKey);
            }
            throw ex;
        }
    }

    private void atualizarIssueOuLancarNaoEncontrado(String issueKey, JiraIssueFields fields) {
        try {
            jiraApiClient.atualizarIssue(issueKey, new JiraIssueRequest(fields));
        } catch (JiraApiException ex) {
            if (ex.getStatus().equals(HttpStatus.NOT_FOUND)) {
                throw new CardNotFoundException(issueKey);
            }
            throw ex;
        }
    }
}

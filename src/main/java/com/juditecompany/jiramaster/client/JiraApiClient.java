package com.juditecompany.jiramaster.client;

import com.juditecompany.jiramaster.client.dto.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class JiraApiClient {

    private final RestClient restClient;

    public JiraApiClient(RestClient jiraRestClient) {
        this.restClient = jiraRestClient;
    }

    public JiraCreatedIssueDto criarIssue(JiraIssueRequest request) {
        return restClient.post()
                .uri("/issue")
                .body(request)
                .retrieve()
                .body(JiraCreatedIssueDto.class);
    }

    public JiraProjectDto buscarProjeto(String projectKey) {
        return restClient.get()
                .uri("/project/{projectKey}", projectKey)
                .retrieve()
                .body(JiraProjectDto.class);
    }

    public java.util.List<JiraCampoDto> listarCampos() {
        return restClient.get()
                .uri("/field")
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<java.util.List<JiraCampoDto>>() {
                });
    }

    private static final String CAMPOS_DA_BUSCA =
            "summary,description,status,priority,issuetype,project,created,updated";

    /**
     * A busca do Jira devolve so os campos projetados. {@code campoExtra} entra nessa lista para
     * campo customizado, cujo id nao e fixo; nulo mantem a projecao padrao.
     */
    public JiraSearchResponseDto buscarIssues(String jql, String campoExtra) {
        String campos = campoExtra == null || campoExtra.isBlank()
                ? CAMPOS_DA_BUSCA
                : CAMPOS_DA_BUSCA + "," + campoExtra;

        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/search/jql").queryParam("jql", jql)
                        .queryParam("fields", campos)
                        .build())
                .retrieve()
                .body(JiraSearchResponseDto.class);
    }

    public JiraIssueDto buscarIssuePorChave(String issueKey) {
        return restClient.get()
                .uri("/issue/{issueKey}", issueKey)
                .retrieve()
                .body(JiraIssueDto.class);
    }

    public void atualizarIssue(String issueKey, JiraIssueRequest request) {
        restClient.put()
                .uri("/issue/{issueKey}", issueKey)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public JiraTransitionsResponseDto buscarTransicoes(String issueKey) {
        return restClient.get()
                .uri("/issue/{issueKey}/transitions", issueKey)
                .retrieve()
                .body(JiraTransitionsResponseDto.class);
    }

    public void executarTransicao(String issueKey, String transitionId) {
        restClient.post()
                .uri("/issue/{issueKey}/transitions", issueKey)
                .body(new JiraTransitionRequest(new JiraTransitionRef(transitionId)))
                .retrieve()
                .toBodilessEntity();
    }

    public JiraCommentDto adicionarComentario(String issueKey, Object corpoAdf) {
        return restClient.post()
                .uri("/issue/{issueKey}/comment", issueKey)
                .body(new JiraCommentRequest(corpoAdf))
                .retrieve()
                .body(JiraCommentDto.class);
    }

    public JiraCommentsResponseDto buscarComentarios(String issueKey) {
        return restClient.get()
                .uri("/issue/{issueKey}/comment", issueKey)
                .retrieve()
                .body(JiraCommentsResponseDto.class);
    }
}

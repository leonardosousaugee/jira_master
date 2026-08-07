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

    public JiraSearchResponseDto buscarIssues(String jql) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/search/jql").queryParam("jql", jql)
                        .queryParam("fields", "summary,description,status,priority,issuetype,project,created,updated")
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
}

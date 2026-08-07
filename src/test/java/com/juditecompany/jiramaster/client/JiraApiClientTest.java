package com.juditecompany.jiramaster.client;

import com.juditecompany.jiramaster.client.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JiraApiClientTest {

    private static final String BASE_URL = "https://juditecompany.atlassian.net/rest/api/3";

    private MockRestServiceServer server;
    private JiraApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new JiraApiClient(builder.build());
    }

    @Test
    void deveCriarIssueEDevolverIdEChave() {
        server.expect(requestTo(BASE_URL + "/issue"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"id":"10001","key":"KAN-1","self":"https://juditecompany.atlassian.net/rest/api/3/issue/10001"}
                        """, MediaType.APPLICATION_JSON));

        JiraIssueRequest request = new JiraIssueRequest(
                new JiraIssueFields(new JiraFieldRef("KAN"), "Titulo", null, new JiraNameRef("Task"), null, null));

        JiraCreatedIssueDto resultado = client.criarIssue(request);

        assertThat(resultado.key()).isEqualTo("KAN-1");
    }

    @Test
    void deveBuscarIssuesComJql() {
        server.expect(requestTo(startsWith(BASE_URL + "/search")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"issues":[{"id":"10001","key":"KAN-1","fields":{"summary":"Titulo","status":{"name":"To Do"},"issuetype":{"name":"Task"},"project":{"key":"KAN"},"created":"2026-08-05T10:00:00.000+0000","updated":"2026-08-05T10:00:00.000+0000"}}]}
                        """, MediaType.APPLICATION_JSON));

        JiraSearchResponseDto resultado = client.buscarIssues("project = KAN AND statusCategory != Done");

        assertThat(resultado.issues()).hasSize(1);
        assertThat(resultado.issues().get(0).key()).isEqualTo("KAN-1");
    }

    @Test
    void deveBuscarIssuePorChave() {
        server.expect(requestTo(BASE_URL + "/issue/KAN-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"10001","key":"KAN-1","fields":{"summary":"Titulo","status":{"name":"To Do"},"issuetype":{"name":"Task"},"project":{"key":"KAN"},"created":"2026-08-05T10:00:00.000+0000","updated":"2026-08-05T10:00:00.000+0000"}}
                        """, MediaType.APPLICATION_JSON));

        JiraIssueDto resultado = client.buscarIssuePorChave("KAN-1");

        assertThat(resultado.fields().summary()).isEqualTo("Titulo");
    }

    @Test
    void deveAtualizarIssue() {
        server.expect(requestTo(BASE_URL + "/issue/KAN-1"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        JiraIssueRequest request = new JiraIssueRequest(
                new JiraIssueFields(null, "Novo titulo", null, null, null, null));

        client.atualizarIssue("KAN-1", request);

        server.verify();
    }

    @Test
    void deveBuscarTransicoesDisponiveis() {
        server.expect(requestTo(BASE_URL + "/issue/KAN-1/transitions"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"transitions":[{"id":"31","name":"Start Progress","to":{"name":"In Progress"}}]}
                        """, MediaType.APPLICATION_JSON));

        JiraTransitionsResponseDto resultado = client.buscarTransicoes("KAN-1");

        assertThat(resultado.transitions()).hasSize(1);
        assertThat(resultado.transitions().get(0).to().name()).isEqualTo("In Progress");
    }

    @Test
    void deveExecutarTransicao() {
        server.expect(requestTo(BASE_URL + "/issue/KAN-1/transitions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        client.executarTransicao("KAN-1", "31");

        server.verify();
    }

    @Test
    void deveAdicionarComentario() {
        server.expect(requestTo(BASE_URL + "/issue/KAN-1/comment"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"id":"10050","author":{"displayName":"Leonardo"},"body":{"type":"doc","version":1,"content":[]},"created":"2026-08-05T10:00:00.000+0000"}
                        """, MediaType.APPLICATION_JSON));

        JiraCommentDto resultado = client.adicionarComentario("KAN-1", java.util.Map.of("type", "doc"));

        assertThat(resultado.author().displayName()).isEqualTo("Leonardo");
    }
}

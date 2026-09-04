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

        JiraSearchResponseDto resultado = client.buscarIssues("project = KAN AND statusCategory != Done", null);

        assertThat(resultado.issues()).hasSize(1);
        assertThat(resultado.issues().get(0).key()).isEqualTo("KAN-1");
    }

    @Test
    void deveProjetarOCampoExtraNaBusca() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("customfield_10073")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"issues\":[]}", MediaType.APPLICATION_JSON));

        client.buscarIssues("project = KAN", "customfield_10073");

        server.verify();
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
    void deveEnviarCampoCustomizadoNoCorpoDaCriacao() {
        server.expect(requestTo(BASE_URL + "/issue"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("\"customfield_10073\":\"agente-alpha\"")))
                .andRespond(withSuccess("""
                        {"id":"10001","key":"KAN-1"}
                        """, MediaType.APPLICATION_JSON));

        JiraIssueRequest request = new JiraIssueRequest(
                new JiraIssueFields(new JiraFieldRef("KAN"), "Titulo", null, new JiraNameRef("Task"), null, null,
                        java.util.Map.of("customfield_10073", "agente-alpha")));

        client.criarIssue(request);

        server.verify();
    }

    @Test
    void deveListarOsCamposDaInstancia() {
        server.expect(requestTo(BASE_URL + "/field"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"id":"summary","name":"Resumo"},{"id":"customfield_10073","name":"Worker","custom":true}]
                        """, MediaType.APPLICATION_JSON));

        java.util.List<JiraCampoDto> resultado = client.listarCampos();

        assertThat(resultado).extracting(JiraCampoDto::id).containsExactly("summary", "customfield_10073");
        assertThat(resultado.get(1).name()).isEqualTo("Worker");
    }

    @Test
    void deveCapturarCampoCustomizadoDaIssue() {
        server.expect(requestTo(BASE_URL + "/issue/KAN-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"10001","key":"KAN-1","fields":{"summary":"Titulo","status":{"name":"To Do"},"issuetype":{"name":"Task"},"project":{"key":"KAN"},"customfield_10073":"agente-alpha","created":"2026-08-05T10:00:00.000+0000","updated":"2026-08-05T10:00:00.000+0000"}}
                        """, MediaType.APPLICATION_JSON));

        JiraIssueDto resultado = client.buscarIssuePorChave("KAN-1");

        assertThat(resultado.fields().textoDoCampo("customfield_10073")).isEqualTo("agente-alpha");
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

    @Test
    void deveListarOsContextosDoCampo() {
        server.expect(requestTo(BASE_URL + "/field/customfield_10365/context"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"values":[{"id":"10001"}]}
                        """, MediaType.APPLICATION_JSON));

        java.util.List<JiraFieldContextDto> resultado = client.listarContextosDoCampo("customfield_10365");

        assertThat(resultado).extracting(JiraFieldContextDto::id).containsExactly("10001");
    }

    @Test
    void deveListarAsOpcoesDoContexto() {
        server.expect(requestTo(BASE_URL + "/field/customfield_10365/context/10001/option"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"values":[{"id":"1","value":"agente-alpha"}]}
                        """, MediaType.APPLICATION_JSON));

        java.util.List<JiraFieldOptionDto> resultado = client.listarOpcoesDoContexto("customfield_10365", "10001");

        assertThat(resultado).extracting(JiraFieldOptionDto::value).containsExactly("agente-alpha");
    }

    @Test
    void deveCriarOpcaoNoContexto() {
        server.expect(requestTo(BASE_URL + "/field/customfield_10365/context/10001/option"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("\"value\":\"agente-alpha\"")))
                .andRespond(withSuccess());

        client.criarOpcao("customfield_10365", "10001", "agente-alpha");

        server.verify();
    }
}

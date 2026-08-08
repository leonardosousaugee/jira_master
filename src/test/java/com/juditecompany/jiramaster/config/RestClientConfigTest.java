package com.juditecompany.jiramaster.config;

import com.juditecompany.jiramaster.exception.JiraApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientConfigTest {

    private static final String BASE_URL = "https://juditecompany.atlassian.net";
    private static final String EMAIL = "leonardo@juditecompany.com";
    private static final String API_TOKEN = "token-secreto";

    private final RestClientConfig restClientConfig = new RestClientConfig();
    private JiraProperties jiraProperties;

    @BeforeEach
    void setUp() {
        jiraProperties = new JiraProperties();
        jiraProperties.setBaseUrl(BASE_URL);
        jiraProperties.setEmail(EMAIL);
        jiraProperties.setApiToken(API_TOKEN);
        jiraProperties.setDefaultProjectKey("KAN");
    }

    private MockRestServiceServer server;

    /**
     * jiraRestClient() builds and returns a finished RestClient, so MockRestServiceServer
     * cannot be bound to it directly. client.mutate() reconstructs a RestClient.Builder that
     * carries over the base URL, default headers and status handler already configured by
     * RestClientConfig, so MockRestServiceServer.bindTo(...) can attach to that builder and a
     * client built from it still exercises the real production wiring.
     */
    private RestClient buildMockedClient() {
        RestClient realClient = restClientConfig.jiraRestClient(jiraProperties);
        RestClient.Builder builder = realClient.mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        return builder.build();
    }

    @Test
    void deveMontarUrlComSufixoDaApiV3() {
        RestClient client = buildMockedClient();

        server.expect(requestTo(BASE_URL + "/rest/api/3/issue/KAN-1"))
                .andRespond(withSuccess());

        client.get().uri("/issue/KAN-1").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void deveEnviarHeaderDeAutenticacaoBasica() {
        String esperado = "Basic " + Base64.getEncoder()
                .encodeToString((EMAIL + ":" + API_TOKEN).getBytes(StandardCharsets.UTF_8));

        RestClient client = buildMockedClient();

        server.expect(requestTo(BASE_URL + "/rest/api/3/issue/KAN-1"))
                .andExpect(header("Authorization", esperado))
                .andRespond(withSuccess());

        client.get().uri("/issue/KAN-1").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void deveLancarJiraApiExceptionParaErro401() {
        RestClient client = buildMockedClient();

        server.expect(requestTo(BASE_URL + "/rest/api/3/issue/KAN-1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorMessages\":[\"token invalido\"]}"));

        JiraApiException excecao = catchThrowableOfType(
                () -> client.get().uri("/issue/KAN-1").retrieve().toBodilessEntity(),
                JiraApiException.class);

        assertThat(excecao.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(excecao.getJiraBody()).contains("token invalido");
    }

    @Test
    void deveLancarJiraApiExceptionParaErro500() {
        RestClient client = buildMockedClient();

        server.expect(requestTo(BASE_URL + "/rest/api/3/issue/KAN-1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorMessages\":[\"erro interno\"]}"));

        JiraApiException excecao = catchThrowableOfType(
                () -> client.get().uri("/issue/KAN-1").retrieve().toBodilessEntity(),
                JiraApiException.class);

        assertThat(excecao.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(excecao.getJiraBody()).contains("erro interno");
    }
}

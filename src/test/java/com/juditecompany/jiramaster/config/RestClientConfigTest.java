package com.juditecompany.jiramaster.config;

import com.juditecompany.jiramaster.exception.JiraApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientConfigTest {

    private static final String CLOUD_ID = "b607f5b3-6193-4cf9-8805-e87c83f26277";
    private static final String ACCESS_TOKEN = "access-token-de-teste";
    private static final String BASE_URL = "https://api.atlassian.com/ex/jira/" + CLOUD_ID + "/rest/api/3";

    private final RestClientConfig restClientConfig = new RestClientConfig();
    private JiraOAuthProperties oauthProperties;
    private JiraOAuthTokenService tokenService;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        oauthProperties = new JiraOAuthProperties();
        oauthProperties.setCloudId(CLOUD_ID);

        tokenService = mock(JiraOAuthTokenService.class);
        when(tokenService.obterAccessToken()).thenReturn(ACCESS_TOKEN);
    }

    /**
     * jiraRestClient() builds and returns a finished RestClient, so MockRestServiceServer
     * cannot be bound to it directly. client.mutate() reconstructs a RestClient.Builder that
     * carries over the base URL, request interceptor and status handler already configured by
     * RestClientConfig, so MockRestServiceServer.bindTo(...) can attach to that builder and a
     * client built from it still exercises the real production wiring.
     */
    private RestClient buildMockedClient() {
        RestClient realClient = restClientConfig.jiraRestClient(oauthProperties, tokenService);
        RestClient.Builder builder = realClient.mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        return builder.build();
    }

    @Test
    void deveMontarUrlComCloudIdESufixoDaApiV3() {
        RestClient client = buildMockedClient();

        server.expect(requestTo(BASE_URL + "/issue/KAN-1"))
                .andRespond(withSuccess());

        client.get().uri("/issue/KAN-1").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void deveEnviarHeaderBearerComOAccessTokenDoTokenService() {
        RestClient client = buildMockedClient();

        server.expect(requestTo(BASE_URL + "/issue/KAN-1"))
                .andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andRespond(withSuccess());

        client.get().uri("/issue/KAN-1").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void deveLancarJiraApiExceptionParaErro401() {
        RestClient client = buildMockedClient();

        server.expect(requestTo(BASE_URL + "/issue/KAN-1"))
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

        server.expect(requestTo(BASE_URL + "/issue/KAN-1"))
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

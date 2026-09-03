package com.juditecompany.jiramaster.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JiraOAuthTokenServiceTest {

    private static final String RESPOSTA_TOKEN =
            "{\"access_token\":\"access-1\",\"refresh_token\":\"refresh-2\",\"expires_in\":3600}";

    private JiraOAuthProperties properties;
    private MockRestServiceServer server;
    private Path arquivoEnv;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        properties = new JiraOAuthProperties();
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setCloudId("cloud-id");
        properties.setRefreshToken("refresh-token-inicial");

        arquivoEnv = tempDir.resolve(".env");
        Files.writeString(arquivoEnv, "OUTRA_VAR=valor\nJIRA_OAUTH_REFRESH_TOKEN=refresh-token-inicial\n");
    }

    private JiraOAuthTokenService criarServico() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new JiraOAuthTokenService(properties, builder, arquivoEnv);
    }

    @Test
    void deveTrocarRefreshTokenPorAccessTokenNaPrimeiraChamada() {
        JiraOAuthTokenService service = criarServico();
        server.expect(requestTo("https://auth.atlassian.com/oauth/token"))
                .andRespond(withSuccess(RESPOSTA_TOKEN, MediaType.APPLICATION_JSON));

        String token = service.obterAccessToken();

        assertThat(token).isEqualTo("access-1");
        server.verify();
    }

    @Test
    void deveReaproveitarOAccessTokenEmCacheSemNovaChamadaHttp() {
        JiraOAuthTokenService service = criarServico();
        server.expect(requestTo("https://auth.atlassian.com/oauth/token"))
                .andRespond(withSuccess(RESPOSTA_TOKEN, MediaType.APPLICATION_JSON));

        service.obterAccessToken();
        String segundoToken = service.obterAccessToken();

        assertThat(segundoToken).isEqualTo("access-1");
        server.verify();
    }

    @Test
    void devePersistirORefreshTokenRenovadoNoArquivoEnvPreservandoOResto() throws IOException {
        JiraOAuthTokenService service = criarServico();
        server.expect(requestTo("https://auth.atlassian.com/oauth/token"))
                .andRespond(withSuccess(RESPOSTA_TOKEN, MediaType.APPLICATION_JSON));

        service.obterAccessToken();

        String conteudo = Files.readString(arquivoEnv, StandardCharsets.UTF_8);
        assertThat(conteudo).contains("JIRA_OAUTH_REFRESH_TOKEN=refresh-2");
        assertThat(conteudo).contains("OUTRA_VAR=valor");
        assertThat(conteudo).doesNotContain("refresh-token-inicial");
    }
}

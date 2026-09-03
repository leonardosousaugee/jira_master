package com.juditecompany.jiramaster.config;

import com.juditecompany.jiramaster.client.dto.JiraOAuthTokenResponse;
import com.juditecompany.jiramaster.exception.JiraApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Troca o refresh token do app OAuth 2.0 (3LO) por um access token de curta duracao, com cache em
 * memoria e renovacao automatica. Basic Auth com token de conta (com ou sem escopo granular) nao
 * aplica escopo de verdade nessa instancia Jira — só OAuth 2.0 respeita `read:jira-work`,
 * `write:jira-work` e `read:jira-user` de verdade.
 *
 * <p>A Atlassian roda (rotate) o refresh token a cada troca — o valor antigo vira invalido — entao
 * o token novo e persistido de volta no `.env` a cada renovacao; sem isso, um restart da aplicacao
 * tentaria renovar com um refresh token ja invalidado.
 */
@Component
public class JiraOAuthTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(JiraOAuthTokenService.class);
    private static final long MARGEM_DE_SEGURANCA_SEGUNDOS = 60;
    private static final Pattern LINHA_REFRESH_TOKEN =
            Pattern.compile("(?m)^JIRA_OAUTH_REFRESH_TOKEN=.*$");
    private static final String URI_TOKEN = "https://auth.atlassian.com/oauth/token";

    private final JiraOAuthProperties properties;
    private final RestClient authRestClient;
    private final Path arquivoEnv;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicReference<String> refreshTokenAtual;

    private volatile String accessTokenEmCache;
    private volatile Instant expiraEm = Instant.MIN;

    public JiraOAuthTokenService(JiraOAuthProperties properties, RestClient.Builder restClientBuilder) {
        this(properties, restClientBuilder, Path.of(".env"));
    }

    JiraOAuthTokenService(JiraOAuthProperties properties, RestClient.Builder restClientBuilder, Path arquivoEnv) {
        this.properties = properties;
        this.authRestClient = restClientBuilder.build();
        this.arquivoEnv = arquivoEnv;
        this.refreshTokenAtual = new AtomicReference<>(properties.getRefreshToken());
    }

    public String obterAccessToken() {
        if (accessTokenEmCache != null && Instant.now().isBefore(expiraEm)) {
            return accessTokenEmCache;
        }
        lock.lock();
        try {
            if (accessTokenEmCache != null && Instant.now().isBefore(expiraEm)) {
                return accessTokenEmCache;
            }
            return renovarToken();
        } finally {
            lock.unlock();
        }
    }

    private String renovarToken() {
        Map<String, String> corpo = Map.of(
                "grant_type", "refresh_token",
                "client_id", properties.getClientId(),
                "client_secret", properties.getClientSecret(),
                "refresh_token", refreshTokenAtual.get());

        JiraOAuthTokenResponse resposta;
        try {
            resposta = authRestClient.post()
                    .uri(URI_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .body(JiraOAuthTokenResponse.class);
        } catch (RestClientResponseException erro) {
            throw new JiraApiException(erro.getStatusCode(), erro.getResponseBodyAsString());
        }

        accessTokenEmCache = resposta.accessToken();
        expiraEm = Instant.now().plusSeconds(resposta.expiresInSegundos() - MARGEM_DE_SEGURANCA_SEGUNDOS);
        refreshTokenAtual.set(resposta.refreshToken());
        persistirRefreshTokenNoEnv(resposta.refreshToken());
        return accessTokenEmCache;
    }

    private void persistirRefreshTokenNoEnv(String novoRefreshToken) {
        try {
            if (!Files.exists(arquivoEnv)) {
                return;
            }
            String conteudo = Files.readString(arquivoEnv, StandardCharsets.UTF_8);
            String linhaNova = "JIRA_OAUTH_REFRESH_TOKEN=" + novoRefreshToken;
            String atualizado = LINHA_REFRESH_TOKEN.matcher(conteudo).replaceFirst(linhaNova);
            if (!atualizado.equals(conteudo)) {
                Files.writeString(arquivoEnv, atualizado, StandardCharsets.UTF_8);
            }
        } catch (IOException erro) {
            LOG.warn("Nao foi possivel persistir o refresh token renovado no .env", erro);
        }
    }
}

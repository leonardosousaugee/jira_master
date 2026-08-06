# Jira Microservice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot REST microservice that exposes card (issue) operations against Jira Cloud, backed by Swagger for manual testing.

**Architecture:** Layered Spring Boot app — `controller` → `service` → `client` (talks HTTP to Jira REST API v3) → mapped through DTOs. Stateless proxy, no database. Config and secrets come from `.env` loaded via `spring-dotenv`.

**Tech Stack:** Java 21, Spring Boot 3.3.4, Maven, springdoc-openapi (Swagger UI), spring-dotenv, JUnit 5 + Mockito + Spring's `MockRestServiceServer`.

## Global Constraints

- Java 21, Spring Boot 3.3.4, Maven — no other build tool.
- No database — this service is a stateless proxy to Jira; Jira is the source of truth.
- Basic Auth to Jira via `email:apiToken` base64, header set once in `RestClient`, token never logged.
- Jira Cloud v3 requires description/comment bodies in Atlassian Document Format (ADF), never plain text — conversion isolated in `AdfMapper`.
- Jira transitions require a transition ID, never a status name — `alterarEtapaCard` resolves the name the caller passes into an ID by looking up available transitions first.
- Config comes from `.env` (`JIRA_BASE_URL`, `JIRA_EMAIL`, `JIRA_API_TOKEN`, `JIRA_DEFAULT_PROJECT_KEY`, `SERVER_PORT`), loaded automatically via `spring-dotenv`. Real `.env` is git-ignored; `.env.example` ships with no real values.
- Default Jira project key is `KAN`, overridable per-request where the API allows it.
- Unit tests (JUnit 5 + Mockito) required for every task with logic. Integration tests against the real Jira API are out of scope for this MVP.

---

### Task 1: Project scaffold + `JiraProperties`

**Files:**
- Create: `pom.xml`
- Create: `src/main/resources/application.yml`
- Create: `.env.example`
- Create: `src/main/java/com/juditecompany/jiramaster/JiraMasterApplication.java`
- Create: `src/main/java/com/juditecompany/jiramaster/config/JiraProperties.java`
- Test: `src/test/java/com/juditecompany/jiramaster/config/JiraPropertiesTest.java`

**Interfaces:**
- Produces: `JiraProperties` with getters `getBaseUrl()`, `getEmail()`, `getApiToken()`, `getDefaultProjectKey()`, all `String`. Later tasks (RestClientConfig, service) consume this bean.

- [ ] **Step 1: Create `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.4</version>
        <relativePath/>
    </parent>

    <groupId>com.juditecompany</groupId>
    <artifactId>jira-master-service</artifactId>
    <version>1.0.0</version>
    <name>jira-master-service</name>
    <description>Microsservico que expoe operacoes de card do Jira via API REST</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.6.0</version>
        </dependency>
        <dependency>
            <groupId>me.paulschwarz</groupId>
            <artifactId>spring-dotenv</artifactId>
            <version>4.0.0</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create `src/main/resources/application.yml`**

```yaml
server:
  port: ${SERVER_PORT:8080}

jira:
  base-url: ${JIRA_BASE_URL}
  email: ${JIRA_EMAIL}
  api-token: ${JIRA_API_TOKEN}
  default-project-key: ${JIRA_DEFAULT_PROJECT_KEY:KAN}

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

- [ ] **Step 3: Create `.env.example`**

```
JIRA_BASE_URL=https://juditecompany.atlassian.net
JIRA_EMAIL=seu.email@empresa.com.br
JIRA_API_TOKEN=coloque_o_token_aqui
JIRA_DEFAULT_PROJECT_KEY=KAN
SERVER_PORT=8080
```

Then create the real `.env` (not committed — already covered by `.gitignore`) with the actual token:

```
JIRA_BASE_URL=https://juditecompany.atlassian.net
JIRA_EMAIL=leonardo.sousa@witzler-ultragaz.com.br
JIRA_API_TOKEN=<token real fornecido pelo usuário>
JIRA_DEFAULT_PROJECT_KEY=KAN
SERVER_PORT=8080
```

- [ ] **Step 4: Create the main application class**

`src/main/java/com/juditecompany/jiramaster/JiraMasterApplication.java`:

```java
package com.juditecompany.jiramaster;

import com.juditecompany.jiramaster.config.JiraProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JiraProperties.class)
public class JiraMasterApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiraMasterApplication.class, args);
    }
}
```

- [ ] **Step 5: Write the failing test for `JiraProperties`**

`src/test/java/com/juditecompany/jiramaster/config/JiraPropertiesTest.java`:

```java
package com.juditecompany.jiramaster.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JiraPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void deveVincularTodasAsPropriedadesQuandoPresentes() {
        contextRunner
                .withPropertyValues(
                        "jira.base-url=https://juditecompany.atlassian.net",
                        "jira.email=leonardo.sousa@witzler-ultragaz.com.br",
                        "jira.api-token=token-de-teste",
                        "jira.default-project-key=KAN")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JiraProperties props = context.getBean(JiraProperties.class);
                    assertThat(props.getBaseUrl()).isEqualTo("https://juditecompany.atlassian.net");
                    assertThat(props.getEmail()).isEqualTo("leonardo.sousa@witzler-ultragaz.com.br");
                    assertThat(props.getApiToken()).isEqualTo("token-de-teste");
                    assertThat(props.getDefaultProjectKey()).isEqualTo("KAN");
                });
    }

    @Test
    void deveFalharQuandoApiTokenEstaAusente() {
        contextRunner
                .withPropertyValues(
                        "jira.base-url=https://juditecompany.atlassian.net",
                        "jira.email=leonardo.sousa@witzler-ultragaz.com.br",
                        "jira.default-project-key=KAN")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(JiraProperties.class)
    static class TestConfig {
    }
}
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `mvn -q -Dtest=JiraPropertiesTest test`
Expected: FAIL — compile error, `JiraProperties` does not exist yet.

- [ ] **Step 7: Implement `JiraProperties`**

`src/main/java/com/juditecompany/jiramaster/config/JiraProperties.java`:

```java
package com.juditecompany.jiramaster.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jira")
public class JiraProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String email;

    @NotBlank
    private String apiToken;

    @NotBlank
    private String defaultProjectKey;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getDefaultProjectKey() {
        return defaultProjectKey;
    }

    public void setDefaultProjectKey(String defaultProjectKey) {
        this.defaultProjectKey = defaultProjectKey;
    }
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn -q -Dtest=JiraPropertiesTest test`
Expected: PASS, both tests green.

- [ ] **Step 9: Commit**

```bash
git add pom.xml src/main/resources/application.yml .env.example \
  src/main/java/com/juditecompany/jiramaster/JiraMasterApplication.java \
  src/main/java/com/juditecompany/jiramaster/config/JiraProperties.java \
  src/test/java/com/juditecompany/jiramaster/config/JiraPropertiesTest.java
git commit -m "feat: scaffold project and bind Jira config properties"
```

---

### Task 2: Exception hierarchy + `GlobalExceptionHandler`

**Files:**
- Create: `src/main/java/com/juditecompany/jiramaster/exception/JiraApiException.java`
- Create: `src/main/java/com/juditecompany/jiramaster/exception/CardNotFoundException.java`
- Create: `src/main/java/com/juditecompany/jiramaster/exception/TransitionNotFoundException.java`
- Create: `src/main/java/com/juditecompany/jiramaster/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/juditecompany/jiramaster/exception/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces: `JiraApiException(HttpStatusCode status, String jiraBody)` with `getStatus()`/`getJiraBody()`; `CardNotFoundException(String issueKey)`; `TransitionNotFoundException(String issueKey, String etapaDestino, List<String> transicoesDisponiveis)` with `getTransicoesDisponiveis()`. The `client` task (Task 4) throws `JiraApiException`; the `service` task (Task 6) throws `CardNotFoundException`/`TransitionNotFoundException`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/juditecompany/jiramaster/exception/GlobalExceptionHandlerTest.java`:

```java
package com.juditecompany.jiramaster.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void deveRetornar404ParaCardNaoEncontrado() {
        ProblemDetail problema = handler.handleCardNaoEncontrado(new CardNotFoundException("KAN-999"));

        assertThat(problema.getStatus()).isEqualTo(404);
        assertThat(problema.getDetail()).contains("KAN-999");
    }

    @Test
    void deveRetornar400ComTransicoesDisponiveis() {
        TransitionNotFoundException ex = new TransitionNotFoundException(
                "KAN-1", "Bloqueado", List.of("To Do", "In Progress", "Done"));

        ProblemDetail problema = handler.handleTransicaoNaoEncontrada(ex);

        assertThat(problema.getStatus()).isEqualTo(400);
        assertThat(problema.getProperties())
                .containsEntry("transicoesDisponiveis", List.of("To Do", "In Progress", "Done"));
    }

    @Test
    void devePropagarStatusDaJiraApiException() {
        JiraApiException ex = new JiraApiException(HttpStatus.UNAUTHORIZED, "{\"errorMessages\":[\"token invalido\"]}");

        ProblemDetail problema = handler.handleJiraApiException(ex);

        assertThat(problema.getStatus()).isEqualTo(401);
    }

    @Test
    void deveRetornar400ParaErroDeValidacao() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError erro = new FieldError("objeto", "titulo", "nao pode ser vazio");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(erro));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ProblemDetail problema = handler.handleValidacao(ex);

        assertThat(problema.getStatus()).isEqualTo(400);
    }

    @Test
    void deveRetornar500ParaErroGenerico() {
        ProblemDetail problema = handler.handleErroGenerico(new RuntimeException("boom"));

        assertThat(problema.getStatus()).isEqualTo(500);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=GlobalExceptionHandlerTest test`
Expected: FAIL — compile errors, none of the classes exist yet.

- [ ] **Step 3: Implement the exception classes**

`src/main/java/com/juditecompany/jiramaster/exception/JiraApiException.java`:

```java
package com.juditecompany.jiramaster.exception;

import org.springframework.http.HttpStatusCode;

public class JiraApiException extends RuntimeException {

    private final HttpStatusCode status;
    private final String jiraBody;

    public JiraApiException(HttpStatusCode status, String jiraBody) {
        super("Jira retornou status " + status.value() + ": " + jiraBody);
        this.status = status;
        this.jiraBody = jiraBody;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getJiraBody() {
        return jiraBody;
    }
}
```

`src/main/java/com/juditecompany/jiramaster/exception/CardNotFoundException.java`:

```java
package com.juditecompany.jiramaster.exception;

public class CardNotFoundException extends RuntimeException {

    public CardNotFoundException(String issueKey) {
        super("Card nao encontrado: " + issueKey);
    }
}
```

`src/main/java/com/juditecompany/jiramaster/exception/TransitionNotFoundException.java`:

```java
package com.juditecompany.jiramaster.exception;

import java.util.List;

public class TransitionNotFoundException extends RuntimeException {

    private final List<String> transicoesDisponiveis;

    public TransitionNotFoundException(String issueKey, String etapaDestino, List<String> transicoesDisponiveis) {
        super("Etapa \"" + etapaDestino + "\" nao e uma transicao valida para o card " + issueKey
                + ". Transicoes disponiveis: " + transicoesDisponiveis);
        this.transicoesDisponiveis = transicoesDisponiveis;
    }

    public List<String> getTransicoesDisponiveis() {
        return transicoesDisponiveis;
    }
}
```

- [ ] **Step 4: Implement `GlobalExceptionHandler`**

`src/main/java/com/juditecompany/jiramaster/exception/GlobalExceptionHandler.java`:

```java
package com.juditecompany.jiramaster.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CardNotFoundException.class)
    public ProblemDetail handleCardNaoEncontrado(CardNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TransitionNotFoundException.class)
    public ProblemDetail handleTransicaoNaoEncontrada(TransitionNotFoundException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problema.setProperty("transicoesDisponiveis", ex.getTransicoesDisponiveis());
        return problema;
    }

    @ExceptionHandler(JiraApiException.class)
    public ProblemDetail handleJiraApiException(JiraApiException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(ex.getStatus().value()), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidacao(MethodArgumentNotValidException ex) {
        List<String> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::toString)
                .collect(Collectors.toList());
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Requisicao invalida");
        problema.setProperty("erros", erros);
        return problema;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleErroGenerico(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q -Dtest=GlobalExceptionHandlerTest test`
Expected: PASS, all 5 tests green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/juditecompany/jiramaster/exception src/test/java/com/juditecompany/jiramaster/exception
git commit -m "feat: add domain exception hierarchy and global exception handler"
```

---

### Task 3: `AdfMapper`

**Files:**
- Create: `src/main/java/com/juditecompany/jiramaster/mapper/AdfMapper.java`
- Test: `src/test/java/com/juditecompany/jiramaster/mapper/AdfMapperTest.java`

**Interfaces:**
- Produces: `AdfMapper.textoParaAdf(String texto): Map<String, Object>` and `AdfMapper.adfParaTexto(JsonNode adf): String`. Task 5 (`JiraCardMapper`) and Task 6 (service) consume both.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/juditecompany/jiramaster/mapper/AdfMapperTest.java`:

```java
package com.juditecompany.jiramaster.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdfMapperTest {

    private final AdfMapper mapper = new AdfMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deveEmbrulharTextoSimplesEmDocumentoAdf() {
        Map<String, Object> adf = mapper.textoParaAdf("Corrigir bug no login");

        assertThat(adf.get("type")).isEqualTo("doc");
        assertThat(adf.get("version")).isEqualTo(1);
    }

    @Test
    void deveExtrairTextoSimplesDeUmDocumentoAdf() throws Exception {
        String json = """
                {
                  "type": "doc",
                  "version": 1,
                  "content": [
                    { "type": "paragraph", "content": [ { "type": "text", "text": "Corrigir bug no login" } ] }
                  ]
                }
                """;
        JsonNode adf = objectMapper.readTree(json);

        String texto = mapper.adfParaTexto(adf);

        assertThat(texto).isEqualTo("Corrigir bug no login");
    }

    @Test
    void deveRetornarStringVaziaQuandoAdfForNulo() {
        assertThat(mapper.adfParaTexto(null)).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=AdfMapperTest test`
Expected: FAIL — `AdfMapper` does not exist.

- [ ] **Step 3: Implement `AdfMapper`**

`src/main/java/com/juditecompany/jiramaster/mapper/AdfMapper.java`:

```java
package com.juditecompany.jiramaster.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AdfMapper {

    public Map<String, Object> textoParaAdf(String texto) {
        Map<String, Object> textNode = new LinkedHashMap<>();
        textNode.put("type", "text");
        textNode.put("text", texto);

        Map<String, Object> paragraph = new LinkedHashMap<>();
        paragraph.put("type", "paragraph");
        paragraph.put("content", List.of(textNode));

        Map<String, Object> documento = new LinkedHashMap<>();
        documento.put("type", "doc");
        documento.put("version", 1);
        documento.put("content", List.of(paragraph));
        return documento;
    }

    public String adfParaTexto(JsonNode adf) {
        if (adf == null || adf.isNull()) {
            return "";
        }
        StringBuilder texto = new StringBuilder();
        coletarTexto(adf, texto);
        return texto.toString().strip();
    }

    private void coletarTexto(JsonNode node, StringBuilder acumulador) {
        if (node.has("text")) {
            acumulador.append(node.get("text").asText());
        }
        if (node.has("content")) {
            for (JsonNode filho : node.get("content")) {
                coletarTexto(filho, acumulador);
            }
            acumulador.append("\n");
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=AdfMapperTest test`
Expected: PASS, all 3 tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/juditecompany/jiramaster/mapper/AdfMapper.java src/test/java/com/juditecompany/jiramaster/mapper/AdfMapperTest.java
git commit -m "feat: add ADF <-> plain text mapper"
```

---

### Task 4: Raw Jira DTOs + `RestClientConfig` + `JiraApiClient`

**Files:**
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraFieldRef.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraNameRef.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraIssueFields.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraIssueRequest.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraCreatedIssueDto.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraStatusDto.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraIssueResponseFields.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraIssueDto.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraSearchResponseDto.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraTransitionToDto.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraTransitionDto.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraTransitionsResponseDto.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraTransitionRef.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraTransitionRequest.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraCommentRequest.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraCommentAuthorDto.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/dto/JiraCommentDto.java`
- Create: `src/main/java/com/juditecompany/jiramaster/config/RestClientConfig.java`
- Create: `src/main/java/com/juditecompany/jiramaster/client/JiraApiClient.java`
- Test: `src/test/java/com/juditecompany/jiramaster/client/JiraApiClientTest.java`

**Interfaces:**
- Consumes: `JiraProperties` (Task 1), `JiraApiException` (Task 2).
- Produces: `JiraApiClient` with methods `criarIssue(JiraIssueRequest): JiraCreatedIssueDto`, `buscarIssues(String jql): JiraSearchResponseDto`, `buscarIssuePorChave(String issueKey): JiraIssueDto`, `atualizarIssue(String issueKey, JiraIssueRequest): void`, `buscarTransicoes(String issueKey): JiraTransitionsResponseDto`, `executarTransicao(String issueKey, String transitionId): void`, `adicionarComentario(String issueKey, Object corpoAdf): JiraCommentDto`. Task 5 (mapper) and Task 6 (service) consume these DTOs and this client.

- [ ] **Step 1: Create the raw Jira DTOs**

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraFieldRef.java`:

```java
package com.juditecompany.jiramaster.client.dto;

public record JiraFieldRef(String key) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraNameRef.java`:

```java
package com.juditecompany.jiramaster.client.dto;

public record JiraNameRef(String name) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraIssueFields.java`:

```java
package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JiraIssueFields(
        JiraFieldRef project,
        String summary,
        Object description,
        JiraNameRef issuetype,
        JiraFieldRef parent,
        JiraNameRef priority
) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraIssueRequest.java`:

```java
package com.juditecompany.jiramaster.client.dto;

public record JiraIssueRequest(JiraIssueFields fields) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraCreatedIssueDto.java`:

```java
package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraCreatedIssueDto(String id, String key) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraStatusDto.java`:

```java
package com.juditecompany.jiramaster.client.dto;

public record JiraStatusDto(String name) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraIssueResponseFields.java`:

```java
package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssueResponseFields(
        String summary,
        JsonNode description,
        JiraStatusDto status,
        JiraNameRef priority,
        JiraNameRef issuetype,
        JiraFieldRef project,
        String created,
        String updated
) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraIssueDto.java`:

```java
package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssueDto(String id, String key, JiraIssueResponseFields fields) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraSearchResponseDto.java`:

```java
package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraSearchResponseDto(List<JiraIssueDto> issues) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraTransitionToDto.java`:

```java
package com.juditecompany.jiramaster.client.dto;

public record JiraTransitionToDto(String name) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraTransitionDto.java`:

```java
package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraTransitionDto(String id, String name, JiraTransitionToDto to) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraTransitionsResponseDto.java`:

```java
package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraTransitionsResponseDto(List<JiraTransitionDto> transitions) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraTransitionRef.java`:

```java
package com.juditecompany.jiramaster.client.dto;

public record JiraTransitionRef(String id) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraTransitionRequest.java`:

```java
package com.juditecompany.jiramaster.client.dto;

public record JiraTransitionRequest(JiraTransitionRef transition) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraCommentRequest.java`:

```java
package com.juditecompany.jiramaster.client.dto;

public record JiraCommentRequest(Object body) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraCommentAuthorDto.java`:

```java
package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraCommentAuthorDto(String displayName) {
}
```

`src/main/java/com/juditecompany/jiramaster/client/dto/JiraCommentDto.java`:

```java
package com.juditecompany.jiramaster.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraCommentDto(String id, JiraCommentAuthorDto author, JsonNode body, String created) {
}
```

- [ ] **Step 2: Write the failing `JiraApiClient` tests**

`src/test/java/com/juditecompany/jiramaster/client/JiraApiClientTest.java`:

```java
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
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q -Dtest=JiraApiClientTest test`
Expected: FAIL — `JiraApiClient` does not exist.

- [ ] **Step 4: Implement `RestClientConfig`**

`src/main/java/com/juditecompany/jiramaster/config/RestClientConfig.java`:

```java
package com.juditecompany.jiramaster.config;

import com.juditecompany.jiramaster.exception.JiraApiException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient jiraRestClient(JiraProperties jiraProperties) {
        String credenciais = jiraProperties.getEmail() + ":" + jiraProperties.getApiToken();
        String basicAuth = Base64.getEncoder().encodeToString(credenciais.getBytes(StandardCharsets.UTF_8));

        return RestClient.builder()
                .baseUrl(jiraProperties.getBaseUrl() + "/rest/api/3")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultStatusHandler(HttpStatusCode::isError, this::lancarJiraApiException)
                .build();
    }

    private void lancarJiraApiException(HttpRequest request, ClientHttpResponse response) throws IOException {
        String corpo = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        throw new JiraApiException(response.getStatusCode(), corpo);
    }
}
```

- [ ] **Step 5: Implement `JiraApiClient`**

`src/main/java/com/juditecompany/jiramaster/client/JiraApiClient.java`:

```java
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
                .uri(uriBuilder -> uriBuilder.path("/search").queryParam("jql", jql).build())
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
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn -q -Dtest=JiraApiClientTest test`
Expected: PASS, all 7 tests green.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/juditecompany/jiramaster/client src/main/java/com/juditecompany/jiramaster/config/RestClientConfig.java src/test/java/com/juditecompany/jiramaster/client
git commit -m "feat: add Jira REST API client with basic auth and error translation"
```

---

### Task 5: `JiraCardMapper`

**Files:**
- Create: `src/main/java/com/juditecompany/jiramaster/dto/response/CardResponse.java`
- Create: `src/main/java/com/juditecompany/jiramaster/dto/response/CardResumoResponse.java`
- Create: `src/main/java/com/juditecompany/jiramaster/dto/response/TransicaoResponse.java`
- Create: `src/main/java/com/juditecompany/jiramaster/dto/response/ComentarioResponse.java`
- Create: `src/main/java/com/juditecompany/jiramaster/mapper/JiraCardMapper.java`
- Test: `src/test/java/com/juditecompany/jiramaster/mapper/JiraCardMapperTest.java`

**Interfaces:**
- Consumes: `JiraIssueDto`, `JiraTransitionDto`, `JiraCommentDto` (Task 4), `AdfMapper` (Task 3).
- Produces: `CardResponse(String issueKey, String titulo, String descricao, String status, String prioridade, String tipoIssue, String projectKey, Instant criadoEm, Instant atualizadoEm)`, `CardResumoResponse(String issueKey, String titulo, String status, String prioridade)`, `TransicaoResponse(String id, String nomeEtapaDestino)`, `ComentarioResponse(String id, String autor, String corpo, Instant criadoEm)`, and `JiraCardMapper` with methods `paraCardResponse(JiraIssueDto): CardResponse`, `paraCardResumoResponse(JiraIssueDto): CardResumoResponse`, `paraTransicaoResponse(JiraTransitionDto): TransicaoResponse`, `paraComentarioResponse(JiraCommentDto): ComentarioResponse`. Task 6 (service) and Task 7 (controller) consume all of these.

- [ ] **Step 1: Create the response DTOs**

`src/main/java/com/juditecompany/jiramaster/dto/response/CardResponse.java`:

```java
package com.juditecompany.jiramaster.dto.response;

import java.time.Instant;

public record CardResponse(
        String issueKey,
        String titulo,
        String descricao,
        String status,
        String prioridade,
        String tipoIssue,
        String projectKey,
        Instant criadoEm,
        Instant atualizadoEm
) {
}
```

`src/main/java/com/juditecompany/jiramaster/dto/response/CardResumoResponse.java`:

```java
package com.juditecompany.jiramaster.dto.response;

public record CardResumoResponse(String issueKey, String titulo, String status, String prioridade) {
}
```

`src/main/java/com/juditecompany/jiramaster/dto/response/TransicaoResponse.java`:

```java
package com.juditecompany.jiramaster.dto.response;

public record TransicaoResponse(String id, String nomeEtapaDestino) {
}
```

`src/main/java/com/juditecompany/jiramaster/dto/response/ComentarioResponse.java`:

```java
package com.juditecompany.jiramaster.dto.response;

import java.time.Instant;

public record ComentarioResponse(String id, String autor, String corpo, Instant criadoEm) {
}
```

- [ ] **Step 2: Write the failing `JiraCardMapper` tests**

`src/test/java/com/juditecompany/jiramaster/mapper/JiraCardMapperTest.java`:

```java
package com.juditecompany.jiramaster.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juditecompany.jiramaster.client.dto.*;
import com.juditecompany.jiramaster.dto.response.CardResponse;
import com.juditecompany.jiramaster.dto.response.ComentarioResponse;
import com.juditecompany.jiramaster.dto.response.TransicaoResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JiraCardMapperTest {

    private final JiraCardMapper mapper = new JiraCardMapper(new AdfMapper());
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deveMapearIssueParaCardResponse() throws Exception {
        var descricao = objectMapper.readTree("""
                {"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"Descricao"}]}]}
                """);
        var fields = new JiraIssueResponseFields(
                "Titulo", descricao, new JiraStatusDto("To Do"), new JiraNameRef("Medium"),
                new JiraNameRef("Task"), new JiraFieldRef("KAN"),
                "2026-08-05T10:00:00.000+0000", "2026-08-05T11:00:00.000+0000");
        var issue = new JiraIssueDto("10001", "KAN-1", fields);

        CardResponse resultado = mapper.paraCardResponse(issue);

        assertThat(resultado.issueKey()).isEqualTo("KAN-1");
        assertThat(resultado.titulo()).isEqualTo("Titulo");
        assertThat(resultado.descricao()).isEqualTo("Descricao");
        assertThat(resultado.status()).isEqualTo("To Do");
        assertThat(resultado.prioridade()).isEqualTo("Medium");
        assertThat(resultado.tipoIssue()).isEqualTo("Task");
        assertThat(resultado.projectKey()).isEqualTo("KAN");
        assertThat(resultado.criadoEm()).isEqualTo(Instant.parse("2026-08-05T10:00:00Z"));
    }

    @Test
    void deveMapearTransicaoParaTransicaoResponse() {
        var transicao = new JiraTransitionDto("31", "Start Progress", new JiraTransitionToDto("In Progress"));

        TransicaoResponse resultado = mapper.paraTransicaoResponse(transicao);

        assertThat(resultado.id()).isEqualTo("31");
        assertThat(resultado.nomeEtapaDestino()).isEqualTo("In Progress");
    }

    @Test
    void deveMapearComentarioParaComentarioResponse() throws Exception {
        var corpo = objectMapper.readTree("""
                {"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"Comentario"}]}]}
                """);
        var comentario = new JiraCommentDto("10050", new JiraCommentAuthorDto("Leonardo"), corpo, "2026-08-05T10:00:00.000+0000");

        ComentarioResponse resultado = mapper.paraComentarioResponse(comentario);

        assertThat(resultado.autor()).isEqualTo("Leonardo");
        assertThat(resultado.corpo()).isEqualTo("Comentario");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q -Dtest=JiraCardMapperTest test`
Expected: FAIL — `JiraCardMapper` does not exist.

- [ ] **Step 4: Implement `JiraCardMapper`**

`src/main/java/com/juditecompany/jiramaster/mapper/JiraCardMapper.java`:

```java
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
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q -Dtest=JiraCardMapperTest test`
Expected: PASS, all 3 tests green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/juditecompany/jiramaster/dto/response src/main/java/com/juditecompany/jiramaster/mapper/JiraCardMapper.java src/test/java/com/juditecompany/jiramaster/mapper/JiraCardMapperTest.java
git commit -m "feat: map raw Jira DTOs into public response DTOs"
```

---

### Task 6: `JiraCardService` + `JiraCardServiceImpl`

**Files:**
- Create: `src/main/java/com/juditecompany/jiramaster/dto/request/CriarCardRequest.java`
- Create: `src/main/java/com/juditecompany/jiramaster/dto/request/EditarCardRequest.java`
- Create: `src/main/java/com/juditecompany/jiramaster/dto/request/AlterarEtapaRequest.java`
- Create: `src/main/java/com/juditecompany/jiramaster/dto/request/AdicionarSubCardRequest.java`
- Create: `src/main/java/com/juditecompany/jiramaster/dto/request/EditarPrioridadeRequest.java`
- Create: `src/main/java/com/juditecompany/jiramaster/dto/request/AdicionarComentarioRequest.java`
- Create: `src/main/java/com/juditecompany/jiramaster/service/JiraCardService.java`
- Create: `src/main/java/com/juditecompany/jiramaster/service/JiraCardServiceImpl.java`
- Test: `src/test/java/com/juditecompany/jiramaster/service/JiraCardServiceImplTest.java`

**Interfaces:**
- Consumes: `JiraApiClient` (Task 4), `JiraCardMapper`/`AdfMapper` (Tasks 3 and 5), `JiraProperties` (Task 1), `CardNotFoundException`/`TransitionNotFoundException` (Task 2).
- Produces: `JiraCardService` interface with `criarCard`, `lerCardsEmAberto`, `buscarCardPorId`, `editarCard`, `listarTransicoesDisponiveis`, `alterarEtapaCard`, `adicionarSubCard`, `editarPrioridade`, `adicionarComentario`. Task 7 (controller) consumes this interface.

- [ ] **Step 1: Create the request DTOs**

`src/main/java/com/juditecompany/jiramaster/dto/request/CriarCardRequest.java`:

```java
package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CriarCardRequest(@NotBlank String titulo, String descricao, String tipoIssue, String projectKey) {
}
```

`src/main/java/com/juditecompany/jiramaster/dto/request/EditarCardRequest.java`:

```java
package com.juditecompany.jiramaster.dto.request;

public record EditarCardRequest(String titulo, String descricao) {
}
```

`src/main/java/com/juditecompany/jiramaster/dto/request/AlterarEtapaRequest.java`:

```java
package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AlterarEtapaRequest(@NotBlank String etapaDestino) {
}
```

`src/main/java/com/juditecompany/jiramaster/dto/request/AdicionarSubCardRequest.java`:

```java
package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdicionarSubCardRequest(@NotBlank String titulo, String descricao) {
}
```

`src/main/java/com/juditecompany/jiramaster/dto/request/EditarPrioridadeRequest.java`:

```java
package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.NotBlank;

public record EditarPrioridadeRequest(@NotBlank String prioridade) {
}
```

`src/main/java/com/juditecompany/jiramaster/dto/request/AdicionarComentarioRequest.java`:

```java
package com.juditecompany.jiramaster.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdicionarComentarioRequest(@NotBlank String comentario) {
}
```

- [ ] **Step 2: Create the `JiraCardService` interface**

`src/main/java/com/juditecompany/jiramaster/service/JiraCardService.java`:

```java
package com.juditecompany.jiramaster.service;

import com.juditecompany.jiramaster.dto.request.*;
import com.juditecompany.jiramaster.dto.response.*;

import java.util.List;

public interface JiraCardService {

    CardResponse criarCard(CriarCardRequest request);

    List<CardResumoResponse> lerCardsEmAberto(String projectKeyOverride);

    CardResponse buscarCardPorId(String issueKey);

    CardResponse editarCard(String issueKey, EditarCardRequest request);

    List<TransicaoResponse> listarTransicoesDisponiveis(String issueKey);

    void alterarEtapaCard(String issueKey, AlterarEtapaRequest request);

    CardResponse adicionarSubCard(String issueKeyPai, AdicionarSubCardRequest request);

    void editarPrioridade(String issueKey, EditarPrioridadeRequest request);

    ComentarioResponse adicionarComentario(String issueKey, AdicionarComentarioRequest request);
}
```

- [ ] **Step 3: Write the failing `JiraCardServiceImpl` tests**

`src/test/java/com/juditecompany/jiramaster/service/JiraCardServiceImplTest.java`:

```java
package com.juditecompany.jiramaster.service;

import com.juditecompany.jiramaster.client.JiraApiClient;
import com.juditecompany.jiramaster.client.dto.*;
import com.juditecompany.jiramaster.config.JiraProperties;
import com.juditecompany.jiramaster.dto.request.*;
import com.juditecompany.jiramaster.dto.response.CardResponse;
import com.juditecompany.jiramaster.exception.CardNotFoundException;
import com.juditecompany.jiramaster.exception.JiraApiException;
import com.juditecompany.jiramaster.exception.TransitionNotFoundException;
import com.juditecompany.jiramaster.mapper.AdfMapper;
import com.juditecompany.jiramaster.mapper.JiraCardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JiraCardServiceImplTest {

    @Mock
    private JiraApiClient jiraApiClient;

    private JiraCardServiceImpl service;
    private final AdfMapper adfMapper = new AdfMapper();
    private final JiraCardMapper cardMapper = new JiraCardMapper(adfMapper);

    private JiraIssueDto issueDeExemplo(String key, String statusNome) {
        var fields = new JiraIssueResponseFields(
                "Titulo", null, new JiraStatusDto(statusNome), new JiraNameRef("Medium"),
                new JiraNameRef("Task"), new JiraFieldRef("KAN"),
                "2026-08-05T10:00:00.000+0000", "2026-08-05T10:00:00.000+0000");
        return new JiraIssueDto("10001", key, fields);
    }

    @BeforeEach
    void setUp() {
        JiraProperties properties = new JiraProperties();
        properties.setBaseUrl("https://juditecompany.atlassian.net");
        properties.setEmail("leonardo.sousa@witzler-ultragaz.com.br");
        properties.setApiToken("token-de-teste");
        properties.setDefaultProjectKey("KAN");

        service = new JiraCardServiceImpl(jiraApiClient, cardMapper, adfMapper, properties);
    }

    @Test
    void deveCriarCardEBuscarDetalhesCompletosDepois() {
        when(jiraApiClient.criarIssue(any())).thenReturn(new JiraCreatedIssueDto("10001", "KAN-1"));
        when(jiraApiClient.buscarIssuePorChave("KAN-1")).thenReturn(issueDeExemplo("KAN-1", "To Do"));

        CardResponse resultado = service.criarCard(new CriarCardRequest("Titulo", "Descricao", "Task", null));

        assertThat(resultado.issueKey()).isEqualTo("KAN-1");
        verify(jiraApiClient).criarIssue(argThat(req -> req.fields().project().key().equals("KAN")));
    }

    @Test
    void deveMontarJqlComProjectKeyPadraoQuandoNenhumForInformado() {
        when(jiraApiClient.buscarIssues(anyString())).thenReturn(new JiraSearchResponseDto(List.of(issueDeExemplo("KAN-1", "To Do"))));

        service.lerCardsEmAberto(null);

        verify(jiraApiClient).buscarIssues("project = KAN AND statusCategory != Done ORDER BY created DESC");
    }

    @Test
    void deveMontarJqlComProjectKeyInformado() {
        when(jiraApiClient.buscarIssues(anyString())).thenReturn(new JiraSearchResponseDto(List.of()));

        service.lerCardsEmAberto("OUTRO");

        verify(jiraApiClient).buscarIssues("project = OUTRO AND statusCategory != Done ORDER BY created DESC");
    }

    @Test
    void deveBuscarCardPorId() {
        when(jiraApiClient.buscarIssuePorChave("KAN-1")).thenReturn(issueDeExemplo("KAN-1", "To Do"));

        CardResponse resultado = service.buscarCardPorId("KAN-1");

        assertThat(resultado.issueKey()).isEqualTo("KAN-1");
    }

    @Test
    void deveTraduzirJiraApiException404ParaCardNotFoundException() {
        when(jiraApiClient.buscarIssuePorChave("KAN-999")).thenThrow(new JiraApiException(HttpStatus.NOT_FOUND, "nao existe"));

        assertThatThrownBy(() -> service.buscarCardPorId("KAN-999"))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void deveEditarCardEDevolverEstadoAtualizado() {
        when(jiraApiClient.buscarIssuePorChave("KAN-1")).thenReturn(issueDeExemplo("KAN-1", "To Do"));

        CardResponse resultado = service.editarCard("KAN-1", new EditarCardRequest("Novo titulo", null));

        assertThat(resultado.issueKey()).isEqualTo("KAN-1");
        verify(jiraApiClient).atualizarIssue(eq("KAN-1"), argThat(req -> req.fields().summary().equals("Novo titulo")));
    }

    @Test
    void deveListarTransicoesDisponiveis() {
        when(jiraApiClient.buscarTransicoes("KAN-1")).thenReturn(new JiraTransitionsResponseDto(
                List.of(new JiraTransitionDto("31", "Start Progress", new JiraTransitionToDto("In Progress")))));

        var resultado = service.listarTransicoesDisponiveis("KAN-1");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nomeEtapaDestino()).isEqualTo("In Progress");
    }

    @Test
    void deveAlterarEtapaQuandoNomeCasaComTransicaoDisponivel() {
        when(jiraApiClient.buscarTransicoes("KAN-1")).thenReturn(new JiraTransitionsResponseDto(
                List.of(new JiraTransitionDto("31", "Start Progress", new JiraTransitionToDto("In Progress")))));

        service.alterarEtapaCard("KAN-1", new AlterarEtapaRequest("in progress"));

        verify(jiraApiClient).executarTransicao("KAN-1", "31");
    }

    @Test
    void deveLancarTransitionNotFoundQuandoNomeNaoCasaComNenhumaTransicao() {
        when(jiraApiClient.buscarTransicoes("KAN-1")).thenReturn(new JiraTransitionsResponseDto(
                List.of(new JiraTransitionDto("31", "Start Progress", new JiraTransitionToDto("In Progress")))));

        assertThatThrownBy(() -> service.alterarEtapaCard("KAN-1", new AlterarEtapaRequest("Bloqueado")))
                .isInstanceOf(TransitionNotFoundException.class);
    }

    @Test
    void deveAdicionarSubCardComParentReferenciado() {
        when(jiraApiClient.criarIssue(any())).thenReturn(new JiraCreatedIssueDto("10002", "KAN-2"));
        when(jiraApiClient.buscarIssuePorChave("KAN-2")).thenReturn(issueDeExemplo("KAN-2", "To Do"));

        service.adicionarSubCard("KAN-1", new AdicionarSubCardRequest("Subtarefa", "Descricao"));

        verify(jiraApiClient).criarIssue(argThat(req ->
                req.fields().parent() != null && req.fields().parent().key().equals("KAN-1")
                        && req.fields().issuetype().name().equals("Subtask")));
    }

    @Test
    void deveEditarPrioridadeEnviandoApenasEsseCampo() {
        service.editarPrioridade("KAN-1", new EditarPrioridadeRequest("High"));

        verify(jiraApiClient).atualizarIssue(eq("KAN-1"), argThat(req ->
                req.fields().priority().name().equals("High") && req.fields().summary() == null));
    }

    @Test
    void deveAdicionarComentario() {
        when(jiraApiClient.adicionarComentario(eq("KAN-1"), any()))
                .thenReturn(new JiraCommentDto("10050",
                        new JiraCommentAuthorDto("Leonardo"), null, "2026-08-05T10:00:00.000+0000"));

        var resultado = service.adicionarComentario("KAN-1", new AdicionarComentarioRequest("Comentario"));

        assertThat(resultado.autor()).isEqualTo("Leonardo");
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn -q -Dtest=JiraCardServiceImplTest test`
Expected: FAIL — `JiraCardServiceImpl` does not exist.

- [ ] **Step 5: Implement `JiraCardServiceImpl`**

`src/main/java/com/juditecompany/jiramaster/service/JiraCardServiceImpl.java`:

```java
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
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn -q -Dtest=JiraCardServiceImplTest test`
Expected: PASS, all 11 tests green.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/juditecompany/jiramaster/dto/request src/main/java/com/juditecompany/jiramaster/service src/test/java/com/juditecompany/jiramaster/service
git commit -m "feat: implement JiraCardService orchestrating client, mapper and ADF"
```

---

### Task 7: `JiraCardController`

**Files:**
- Create: `src/main/java/com/juditecompany/jiramaster/controller/JiraCardController.java`
- Test: `src/test/java/com/juditecompany/jiramaster/controller/JiraCardControllerTest.java`

**Interfaces:**
- Consumes: `JiraCardService` (Task 6), all request/response DTOs (Tasks 5 and 6).
- Produces: HTTP endpoints under `/api/cards` per the API contract in the design spec.

- [ ] **Step 1: Write the failing controller tests**

`src/test/java/com/juditecompany/jiramaster/controller/JiraCardControllerTest.java`:

```java
package com.juditecompany.jiramaster.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juditecompany.jiramaster.dto.response.CardResponse;
import com.juditecompany.jiramaster.exception.TransitionNotFoundException;
import com.juditecompany.jiramaster.service.JiraCardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JiraCardController.class)
class JiraCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JiraCardService service;

    @Test
    void deveCriarCardERetornar201() throws Exception {
        CardResponse resposta = new CardResponse("KAN-1", "Titulo", "Descricao", "To Do", "Medium", "Task", "KAN",
                Instant.parse("2026-08-05T10:00:00Z"), Instant.parse("2026-08-05T10:00:00Z"));
        when(service.criarCard(any())).thenReturn(resposta);

        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Titulo","descricao":"Descricao"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issueKey").value("KAN-1"));
    }

    @Test
    void deveRetornar400QuandoTituloEstiverAusente() throws Exception {
        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveBuscarCardPorIdERetornar200() throws Exception {
        CardResponse resposta = new CardResponse("KAN-1", "Titulo", "Descricao", "To Do", "Medium", "Task", "KAN",
                Instant.parse("2026-08-05T10:00:00Z"), Instant.parse("2026-08-05T10:00:00Z"));
        when(service.buscarCardPorId("KAN-1")).thenReturn(resposta);

        mockMvc.perform(get("/api/cards/KAN-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Titulo"));
    }

    @Test
    void deveAlterarEtapaERetornar204() throws Exception {
        mockMvc.perform(post("/api/cards/KAN-1/etapa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"etapaDestino":"In Progress"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void deveRetornar400QuandoEtapaNaoExistir() throws Exception {
        org.mockito.Mockito.doThrow(new TransitionNotFoundException("KAN-1", "Bloqueado", List.of("To Do", "Done")))
                .when(service).alterarEtapaCard(eq("KAN-1"), any());

        mockMvc.perform(post("/api/cards/KAN-1/etapa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"etapaDestino":"Bloqueado"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=JiraCardControllerTest test`
Expected: FAIL — `JiraCardController` does not exist.

- [ ] **Step 3: Implement `JiraCardController`**

`src/main/java/com/juditecompany/jiramaster/controller/JiraCardController.java`:

```java
package com.juditecompany.jiramaster.controller;

import com.juditecompany.jiramaster.dto.request.*;
import com.juditecompany.jiramaster.dto.response.*;
import com.juditecompany.jiramaster.service.JiraCardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cards")
@Tag(name = "Cards", description = "Operacoes sobre cards (issues) do Jira")
public class JiraCardController {

    private final JiraCardService service;

    public JiraCardController(JiraCardService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CardResponse> criarCard(@Valid @RequestBody CriarCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarCard(request));
    }

    @GetMapping("/abertos")
    public ResponseEntity<List<CardResumoResponse>> lerCardsEmAberto(
            @RequestParam(required = false) String projectKey) {
        return ResponseEntity.ok(service.lerCardsEmAberto(projectKey));
    }

    @GetMapping("/{issueKey}")
    public ResponseEntity<CardResponse> buscarCardPorId(@PathVariable String issueKey) {
        return ResponseEntity.ok(service.buscarCardPorId(issueKey));
    }

    @PatchMapping("/{issueKey}")
    public ResponseEntity<CardResponse> editarCard(@PathVariable String issueKey,
                                                     @RequestBody EditarCardRequest request) {
        return ResponseEntity.ok(service.editarCard(issueKey, request));
    }

    @GetMapping("/{issueKey}/transicoes")
    public ResponseEntity<List<TransicaoResponse>> listarTransicoesDisponiveis(@PathVariable String issueKey) {
        return ResponseEntity.ok(service.listarTransicoesDisponiveis(issueKey));
    }

    @PostMapping("/{issueKey}/etapa")
    public ResponseEntity<Void> alterarEtapaCard(@PathVariable String issueKey,
                                                   @Valid @RequestBody AlterarEtapaRequest request) {
        service.alterarEtapaCard(issueKey, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{issueKey}/subcards")
    public ResponseEntity<CardResponse> adicionarSubCard(@PathVariable String issueKey,
                                                           @Valid @RequestBody AdicionarSubCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adicionarSubCard(issueKey, request));
    }

    @PatchMapping("/{issueKey}/prioridade")
    public ResponseEntity<Void> editarPrioridade(@PathVariable String issueKey,
                                                   @Valid @RequestBody EditarPrioridadeRequest request) {
        service.editarPrioridade(issueKey, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{issueKey}/comentarios")
    public ResponseEntity<ComentarioResponse> adicionarComentario(@PathVariable String issueKey,
                                                                    @Valid @RequestBody AdicionarComentarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adicionarComentario(issueKey, request));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=JiraCardControllerTest test`
Expected: PASS, all 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/juditecompany/jiramaster/controller src/test/java/com/juditecompany/jiramaster/controller
git commit -m "feat: expose Jira card operations as REST endpoints"
```

---

### Task 8: Swagger config + manual smoke test against real Jira

**Files:**
- Create: `src/main/java/com/juditecompany/jiramaster/config/OpenApiConfig.java`

**Interfaces:**
- Produces: `/swagger-ui.html` and `/v3/api-docs`, used to manually exercise every endpoint against the real `juditecompany.atlassian.net` Jira site.

- [ ] **Step 1: Implement `OpenApiConfig`**

`src/main/java/com/juditecompany/jiramaster/config/OpenApiConfig.java`:

```java
package com.juditecompany.jiramaster.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI jiraMasterOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Jira Master Service")
                .description("Microsservico que expoe operacoes de card do Jira via API REST")
                .version("1.0.0"));
    }
}
```

- [ ] **Step 2: Run the full test suite**

Run: `mvn test`
Expected: PASS, every test from Tasks 1–7 green.

- [ ] **Step 3: Start the service and confirm the boot succeeds**

Run: `mvn spring-boot:run`
Expected: Log shows `Started JiraMasterApplication` with no `.env`-related startup failure. Confirms `.env` was created in Task 1 with a real `JIRA_API_TOKEN`.

- [ ] **Step 4: Manual smoke test via Swagger UI against the real Jira site**

1. Open `http://localhost:8080/swagger-ui.html`.
2. `POST /api/cards` with `{"titulo": "Teste microsservico", "descricao": "Card de teste do smoke test"}` — expect `201`, copy the returned `issueKey`.
3. `GET /api/cards/{issueKey}` — expect `200` with the same `titulo`/`descricao`.
4. `GET /api/cards/{issueKey}/transicoes` — note the returned `nomeEtapaDestino` values.
5. `POST /api/cards/{issueKey}/etapa` with one of the names noted above — expect `204`.
6. `POST /api/cards/{issueKey}/comentarios` with `{"comentario": "Comentario de teste"}` — expect `201`.
7. `GET /api/cards/abertos` — expect the card to show up with its new status.
8. Open `https://juditecompany.atlassian.net/browse/{issueKey}` in a browser and confirm the title, comment, and status match what the API shows.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/juditecompany/jiramaster/config/OpenApiConfig.java
git commit -m "feat: add Swagger UI configuration"
```

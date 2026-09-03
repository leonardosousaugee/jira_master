# Configurar OAuth 2.0 (3LO) para o `jira_master`

## Por que OAuth e não API token

Testamos, nessa ordem, contra `leo91363769.atlassian.net`:

- **Token clássico** (Basic Auth, sem escopo): funciona, mas dá acesso total à conta —
  enxerga todo projeto que a conta enxerga, sem nenhuma restrição.
- **API token with scopes** (Basic Auth, com escopo granular): autentica, mas devolve
  **zero projetos** (`GET /project/search` → `total:0`), em qualquer projeto, tipo de
  projeto (team-managed ou company-managed) ou combinação testada. Recurso quebrado
  nessa instância — não é erro de configuração.
- **Service account** (`admin.atlassian.com` → Service accounts): mesmo resultado do
  token com escopo — autentica, zero projetos. Usa o mesmo motor de escopo granular.
- **OAuth 2.0 (3LO)**: funciona corretamente, aplica os escopos de verdade e enxerga os
  projetos esperados.

Conclusão: nessa instância, **só OAuth 2.0 (3LO) aplica escopo de verdade**. É mais
trabalho pra configurar (um app registrado + consentimento manual único), mas é o único
caminho que funciona sem dar acesso total à conta.

## Passo 1 — Registrar o app OAuth 2.0

1. Acesse [developer.atlassian.com/console/myapps](https://developer.atlassian.com/console/myapps/).
2. **Create** → **OAuth 2.0 integration** → dê um nome (ex.: `jira_master-auth`).
3. Marque a opção de **resource-level** (restringe ao site que você selecionar na
   autorização, em vez de todo site da conta) e aceite os termos de desenvolvedor.

## Passo 2 — Configurar os escopos (aba Permissions)

1. Aba **Permissions** → **Add** → localize **Jira API** e adicione.
2. Ao lado de "Jira API", clique em **Configure** (não só "Add" — "Add" só vincula a API,
   "Configure" é onde marca os escopos de verdade).
3. Use a aba de **escopos granulares** (Granular scopes, não Classic) e marque:
   - `read:jira-work`
   - `write:jira-work`
   - `read:jira-user`
4. **Save**.

## Passo 3 — Configurar o callback (aba Authorization)

1. Aba **Authorization** → **OAuth 2.0 (3LO)** → **Configure**.
2. Adicione uma **Callback URL**: `https://localhost/callback`. Não precisa haver
   nenhum servidor real ouvindo ali — é só pra capturar o `code` na barra de endereço do
   navegador depois do consentimento, mesmo que a página dê erro de conexão.

## Passo 4 — Autorização manual (só uma vez)

Monte a URL de autorização com o **client id** do app (aba **Settings**):

```
https://auth.atlassian.com/authorize?audience=api.atlassian.com&client_id=SEU_CLIENT_ID&scope=read%3Ajira-work%20write%3Ajira-work%20read%3Ajira-user%20offline_access&redirect_uri=https%3A%2F%2Flocalhost%2Fcallback&state=algum_valor&response_type=code&prompt=consent
```

Abra no navegador, faça login com a conta que tem acesso ao projeto, selecione o site
(`leo91363769.atlassian.net`) e autorize. O navegador tenta carregar
`https://localhost/callback?code=...&state=...` e normalmente dá erro de conexão — **é
esperado**. Copie a URL completa da barra de endereço; o parâmetro `code` é de uso
único e expira em minutos.

## Passo 5 — Trocar o code por tokens

```bash
curl --request POST \
  --url https://auth.atlassian.com/oauth/token \
  --header 'Content-Type: application/json' \
  --data '{
    "grant_type": "authorization_code",
    "client_id": "SEU_CLIENT_ID",
    "client_secret": "SEU_CLIENT_SECRET",
    "code": "CODE_DA_URL_DE_REDIRECT",
    "redirect_uri": "https://localhost/callback"
  }'
```

A resposta traz `access_token` (curta duração, ~1h), `refresh_token` e `expires_in`.

## Passo 6 — Descobrir o cloudId do site

```bash
curl --header "Authorization: Bearer ACCESS_TOKEN" \
  https://api.atlassian.com/oauth/token/accessible-resources
```

Devolve uma lista de sites acessíveis; o campo `id` de cada um é o `cloudId` usado nas
chamadas de API (`https://api.atlassian.com/ex/jira/{cloudId}/rest/api/3/...`).

## Passo 7 — Preencher o `.env`

```
JIRA_OAUTH_CLIENT_ID=<client id do app>
JIRA_OAUTH_CLIENT_SECRET=<client secret do app>
JIRA_OAUTH_CLOUD_ID=<cloudId do passo 6>
JIRA_OAUTH_REFRESH_TOKEN=<refresh_token do passo 5>
```

## Depois disso, é automático

A Atlassian **roda (rotate) o refresh token a cada uso**: toda vez que a aplicação troca
o refresh token por um access token novo, ela recebe também um refresh token novo, e o
antigo vira inválido. Por isso `JiraOAuthTokenService`
(`src/main/java/.../config/JiraOAuthTokenService.java`) reescreve a linha
`JIRA_OAUTH_REFRESH_TOKEN=` no `.env` a cada renovação — sem isso, um restart da
aplicação tentaria renovar com um refresh token já invalidado e falharia. Os Passos 1–6
só se repetem se o app OAuth for revogado ou o refresh token expirar por falta de uso
(90 dias sem renovar).

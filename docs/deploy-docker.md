# Guia de Deploy com Docker (.tar e .sh)

Este guia ensina o passo a passo para empacotar o **`jira_master`** em uma imagem Docker, exportá-la para um arquivo compactado `.tar`, transferi-la para a pasta padrão em um servidor Linux e executar a aplicação utilizando o script [`start-jira-master.sh`](../start-jira-master.sh).

Esse formato de deploy é ideal para servidores que não possuem acesso direto a um registry privado de imagens (como Docker Hub ou AWS ECR) ou quando se deseja um deploy direto e padronizado.

---

## Índice

- [Estrutura Padrão de Pastas no Servidor](#estrutura-padrão-de-pastas-no-servidor)
- [Visão Geral do Fluxo](#visão-geral-do-fluxo)
- [Arquivos Necessários](#arquivos-necessários)
- [Passo 1: Gerar a Imagem e o Arquivo `.tar` (Local / CI)](#passo-1-gerar-a-imagem-e-o-arquivo-tar-local--ci)
- [Passo 2: Preparar o Diretório e Transferir Arquivos para o Servidor](#passo-2-preparar-o-diretório-e-transferir-arquivos-para-o-servidor)
- [Passo 3: Executar o Deploy no Servidor Linux](#passo-3-executar-o-deploy-no-servidor-linux)
- [Como Funciona o `start-jira-master.sh`](#como-funciona-o-start-jira-mastersh)
- [Operação e Comandos Úteis no Servidor](#operação-e-comandos-úteis-no-servidor)
- [Troubleshooting (Problemas Comuns)](#troubleshooting-problemas-comuns)

---

## Estrutura Padrão de Pastas no Servidor

No servidor Linux, a aplicação deve residir no seguinte padrão de diretório:

```text
/opt/jira-master/                     <-- Pasta padrão da aplicação (ou ~/apps/jira-master/)
├── run-jira-master.tar               <-- Imagem Docker exportada (.tar)
├── start-jira-master.sh              <-- Script de deploy e inicialização (.sh)
└── .env                              <-- Variáveis de ambiente e credenciais
```

> [!TIP]
> **Convenção de Localização:**
> - **Servidores de Produção / Homologação (Recomendado):** `/opt/jira-master/`
> - **Servidores com Usuário sem sudo / Dev:** `~/apps/jira-master/` ou `/home/$USER/jira-master/`

---

## Visão Geral do Fluxo

```
[ Máquina Local / Build ]
   1. docker build  ──> Cria a imagem 'jira-master-image'
   2. docker save   ──> Exporta a imagem para 'run-jira-master.tar'
         │
         │ (Transferência via scp / rsync / sftp)
         ▼
[ Servidor Linux: /opt/jira-master/ ]
   3. chmod +x start-jira-master.sh
   4. ./start-jira-master.sh
         ├── docker kill / docker rm  (encerra container anterior)
         ├── docker load              (carrega imagem do .tar)
         ├── docker run               (inicia container com .env e porta 8080)
         └── docker attach            (exibe logs de inicialização)
```

---

## Arquivos Necessários

Para subir a aplicação no servidor Linux, você precisará de 3 arquivos dentro da pasta `/opt/jira-master/`:

| Arquivo | Origem | Descrição |
|---|---|---|
| `run-jira-master.tar` | Gerado via `docker save` | Imagem Docker exportada |
| `start-jira-master.sh` | Raiz do repositório | Script Bash que automatiza a carga e subida do container |
| `.env` | Criado a partir do `.env.example` | Credenciais e configurações do Jira Cloud |

---

## Passo 1: Gerar a Imagem e o Arquivo `.tar` (Local / CI)

Na raiz do projeto na sua máquina de desenvolvimento ou esteira de CI:

### 1.1 Construir a imagem Docker

Execute o comando de build:

```bash
docker build -t jira-master-image .
```

> [!IMPORTANT]
> **Build multiplataforma (Mac Apple Silicon / ARM ou Windows):**
> Se você estiver construindo a imagem em uma máquina ARM (Mac M1/M2/M3) ou Windows e o servidor Linux de destino for arquitetura **x86_64 / amd64**, adicione a flag `--platform linux/amd64`:
> ```bash
> docker build --platform linux/amd64 -t jira-master-image .
> ```

### 1.2 Exportar a imagem para o arquivo `.tar`

Com a imagem construída, use o comando `docker save`:

```bash
docker save -o run-jira-master.tar jira-master-image
```

> [!NOTE]
> O nome do arquivo gerado **deve ser** `run-jira-master.tar` e a tag **deve ser** `jira-master-image`, pois são os nomes esperados pelo script `start-jira-master.sh`.

---

## Passo 2: Preparar o Diretório e Transferir Arquivos para o Servidor

### 2.1 Criar a pasta padrão no servidor (se for o primeiro deploy)

Conecte no servidor Linux via SSH e crie a pasta `/opt/jira-master/` com as devidas permissões:

```bash
ssh usuario@ip_do_servidor "sudo mkdir -p /opt/jira-master && sudo chown -R \$USER:\$USER /opt/jira-master"
```

### 2.2 Transferir os arquivos

Envie o arquivo `.tar`, o script `.sh` e o `.env` configurado para `/opt/jira-master/`:

#### Exemplo com `scp`:

```bash
scp run-jira-master.tar start-jira-master.sh .env usuario@ip_do_servidor:/opt/jira-master/
```

#### Exemplo com `rsync` (com barra de progresso e retentativa):

```bash
rsync -avzP run-jira-master.tar start-jira-master.sh .env usuario@ip_do_servidor:/opt/jira-master/
```

*(Alternativamente, você pode usar clientes SFTP como WinSCP, FileZilla ou extensões de SSH do VS Code apontando para `/opt/jira-master/`).*

---

## Passo 3: Executar o Deploy no Servidor Linux

### 3.1 Conectar no servidor e acessar a pasta padrão

```bash
ssh usuario@ip_do_servidor
cd /opt/jira-master
```

### 3.2 Verificar as variáveis de ambiente

Certifique-se de que o arquivo `.env` está presente na pasta e preenchido com as credenciais corretas:

```bash
cat .env
```

Campos essenciais:
- `JIRA_OAUTH_CLIENT_ID`
- `JIRA_OAUTH_CLIENT_SECRET`
- `JIRA_OAUTH_CLOUD_ID`
- `JIRA_OAUTH_REFRESH_TOKEN`

### 3.3 Dar permissão de execução ao script

```bash
chmod +x start-jira-master.sh
```

### 3.4 Executar o script

```bash
./start-jira-master.sh
```

---

## Como Funciona o `start-jira-master.sh`

O script [`start-jira-master.sh`](../start-jira-master.sh) é executado dentro de `/opt/jira-master/` e realiza as seguintes etapas:

```bash
#!/bin/bash
PROJECT="jira-master"

IMAGE_TAR="run-$PROJECT.tar"         # run-jira-master.tar
IMAGE_NAME="$PROJECT-image"          # jira-master-image
CONTAINER_NAME="$PROJECT-container"   # jira-master-container

echo ">> Parando container existente: $CONTAINER_NAME"
docker kill "$CONTAINER_NAME" >/dev/null || true

echo ">> Removendo container existente: $CONTAINER_NAME"
docker rm -f "$CONTAINER_NAME" >/dev/null || true

echo ">> Carregando imagem"
docker load -i "$IMAGE_TAR"

echo ">> Inicializando container"
docker run -e TZ=America/Sao_Paulo \
  --cpus="1" \
  -p 8080:8080 \
  --add-host=host.docker.internal:host-gateway \
  --env-file .env \
  --rm \
  -it \
  -d \
  --name "$CONTAINER_NAME" \
  "$IMAGE_NAME"

echo ">>Container inicializado"
docker attach "$CONTAINER_NAME"
```

### Significado dos parâmetros do `docker run`:

- `-e TZ=America/Sao_Paulo`: Configura o fuso horário da aplicação para o horário de Brasília.
- `--cpus="1"`: Limita o consumo de CPU do container a 1 núcleo.
- `-p 8080:8080`: Mapeia a porta `8080` do host para a porta `8080` do container.
- `--add-host=host.docker.internal:host-gateway`: Permite que o container acesse serviços rodando no host através do hostname `host.docker.internal`.
- `--env-file .env`: Injeta as variáveis de ambiente do arquivo `.env` dentro do container.
- `--rm`: Remove o container automaticamente caso ele seja encerrado.
- `-d`: Executa o container em segundo plano (detached mode).
- `--name jira-master-container`: Define o nome identificador do container.
- `docker attach`: Conecta o terminal atual à saída do container para acompanhar os logs de inicialização do Spring Boot.

---

## Operação e Comandos Úteis no Servidor

### Como sair do `docker attach` sem parar o container

> [!WARNING]
> Se você pressionar `Ctrl + C` enquanto estiver conectado pelo `docker attach`, o container receberá um sinal `SIGINT` e **será finalizado**!
>
> Para sair do modo de visualização mantendo o container rodando em segundo plano:
> Pressione **`Ctrl + P`** seguido de **`Ctrl + Q`**.

### Ver logs em tempo real

Caso tenha se desconectado do container, você pode acompanhar os logs com:

```bash
docker logs -f jira-master-container
```

*(Para sair dos logs, pressione `Ctrl + C` — ao contrário do attach, `docker logs` não mata o container).*

### Verificar se o container está ativo

```bash
docker ps --filter "name=jira-master-container"
```

### Testar a saúde da API no servidor

```bash
curl -i http://localhost:8080/v3/api-docs
```

Ou acesse no navegador: `http://ip_do_servidor:8080/swagger-ui.html`

### Parar o serviço manualmente

```bash
docker stop jira-master-container
```

### Limpar imagens antigas / não utilizadas no servidor

Para economizar espaço em disco após novos deploys:

```bash
docker image prune -f
```

---

## Troubleshooting (Problemas Comuns)

### 1. Erro de quebra de linha no Linux: `\r: command not found`
Se o arquivo `start-jira-master.sh` foi salvo ou editado no Windows com finais de linha no padrão CRLF, o Linux falhará ao executar.
- **Solução:**
  ```bash
  sed -i 's/\r$//' start-jira-master.sh
  # ou
  dos2unix start-jira-master.sh
  ```

### 2. Erro: `exec format error`
Ocorre se a imagem foi construída em arquitetura diferente da do servidor (ex.: build feito em Mac M1/M2/M3 ARM e executado em servidor Linux x86_64).
- **Solução:** Gere novamente o build especificando a plataforma correta:
  ```bash
  docker build --platform linux/amd64 -t jira-master-image .
  docker save -o run-jira-master.tar jira-master-image
  ```

### 3. Erro: `open .env: no such file or directory`
O script necessita do arquivo `.env` na mesma pasta (`/opt/jira-master/`) de onde o comando está sendo executado.
- **Solução:** Copie o `.env.example` para `.env` e configure suas variáveis:
  ```bash
  cp .env.example .env
  nano .env
  ```

### 4. Porta 8080 já está em uso (`port is already allocated`)
Outro processo ou container já está escutando na porta 8080.
- **Solução:**
  Identifique e finalize o processo conflitante:
  ```bash
  sudo lsof -i :8080
  # ou
  sudo netstat -tulpn | grep 8080
  ```

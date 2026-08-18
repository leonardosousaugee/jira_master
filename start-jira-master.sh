#!/bin/bash
PROJECT="jira-master"

IMAGE_TAR="run-$PROJECT.tar"
IMAGE_NAME="$PROJECT-image"
CONTAINER_NAME="$PROJECT-container"

echo ">> Parando container existente: $CONTAINER_NAME"
docker kill "$CONTAINER_NAME" >/dev/null || true

echo ">> Removendo container existente: $CONTAINER_NAME"
docker rm -f "$CONTAINER_NAME" >/dev/null || true

echo ">> Carregando imagem"
docker load -i "$IMAGE_TAR"

echo ">>  Inicializando container"
docker run -e TZ=America/Sao_Paulo --cpus="1" -p 8080:8080 --env-file .env --rm -it -d --name "$CONTAINER_NAME" "$IMAGE_NAME"

echo ">>Container inicializado"
docker attach "$CONTAINER_NAME"
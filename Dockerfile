# Build
FROM maven:3.9-eclipse-temurin-21 AS build

# Rede corporativa faz inspecao TLS (Zscaler) - confiar na CA antes de qualquer chamada HTTPS
# (Maven baixando dependencias, e o app em runtime falando com o Jira Cloud). Sem isso, chamadas
# HTTPS ao Jira falham com "PKIX path building failed".
# A CA nao fica versionada no git: buscamos o certificado direto de uma conexao HTTPS de teste
# (google.com) no momento do build. Se a rede intercepta TLS, o topo da cadeia retornada e a CA
# da propria inspecao; se nao intercepta, e a CA publica do Google - nos dois casos e seguro confiar.
RUN echo | openssl s_client -connect google.com:443 -servername google.com -showcerts 2>/dev/null > /tmp/chain.pem \
    && csplit -s -z -f /tmp/chain-cert- -b '%02d.pem' /tmp/chain.pem '/-----BEGIN CERTIFICATE-----/' '{*}' \
    && CA_CERT=$(ls -1 /tmp/chain-cert-*.pem | tail -1) \
    && keytool -importcert -noprompt -trustcacerts \
       -alias corp-proxy-ca -file "$CA_CERT" \
       -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit \
    && rm -f /tmp/chain.pem /tmp/chain-cert-*.pem

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Runtime
FROM eclipse-temurin:21-jre

RUN echo | openssl s_client -connect google.com:443 -servername google.com -showcerts 2>/dev/null > /tmp/chain.pem \
    && csplit -s -z -f /tmp/chain-cert- -b '%02d.pem' /tmp/chain.pem '/-----BEGIN CERTIFICATE-----/' '{*}' \
    && CA_CERT=$(ls -1 /tmp/chain-cert-*.pem | tail -1) \
    && keytool -importcert -noprompt -trustcacerts \
       -alias corp-proxy-ca -file "$CA_CERT" \
       -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit \
    && rm -f /tmp/chain.pem /tmp/chain-cert-*.pem

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
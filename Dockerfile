# Etapa 1: build do EAR com Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -q -B package -DskipTests

# Etapa 2: WildFly com o datasource configurado e o EAR implantado
FROM quay.io/wildfly/wildfly:41.0.1.Final-jdk21

ARG POSTGRES_DRIVER_VERSION=42.7.14

USER root
ADD https://repo1.maven.org/maven2/org/postgresql/postgresql/${POSTGRES_DRIVER_VERSION}/postgresql-${POSTGRES_DRIVER_VERSION}.jar /tmp/postgresql.jar
COPY docker/configurar-wildfly.cli /tmp/configurar-wildfly.cli
RUN chown jboss:root /tmp/postgresql.jar /tmp/configurar-wildfly.cli

USER jboss
RUN /opt/jboss/wildfly/bin/jboss-cli.sh --file=/tmp/configurar-wildfly.cli \
    && rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history \
              /opt/jboss/wildfly/standalone/data /opt/jboss/wildfly/standalone/log

COPY --from=build --chown=jboss:root /app/planning-ear/target/planning.ear /opt/jboss/wildfly/standalone/deployments/

EXPOSE 8080 9990
# -c standalone-full.xml: perfil com JMS (o padrão, standalone.xml, não tem mensageria)
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-c", "standalone-full.xml", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]

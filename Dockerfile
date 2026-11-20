# ---------- Estágio 1: compilar ----------
# Imagem com JDK completo + Maven (via wrapper). Só existe durante o build, não vai para a imagem final.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copiar primeiro só o pom: enquanto ele não mudar, o Docker reaproveita o cache das dependências.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar

# ---------- Estágio 2: rodar ----------
# Imagem final só com o JRE (bem menor que o JDK + Maven + código-fonte).
FROM eclipse-temurin:21-jre
WORKDIR /app

# Usuário sem privilégios: se alguém explorar a aplicação, não ganha root dentro do container.
RUN useradd --system --create-home appuser
COPY --from=build /app/app.jar app.jar
USER appuser

# Dentro do container a porta é sempre 8080; no host, o compose escolhe (padrão 8081).
ENV SERVER_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

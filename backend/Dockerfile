# --- Faza 1: build ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew
COPY src src
RUN ./gradlew bootJar --no-daemon

# --- Faza 2: runtime ---
FROM eclipse-temurin:25-jre
WORKDIR /app

# Docker CLI, da DockerRunner moze da pokrece kontejnere preko montiranog soketa hosta.
# Statican binarni fajl, bez dodavanja APT repozitorijuma (manje krhko).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && curl -fsSL https://download.docker.com/linux/static/stable/x86_64/docker-27.3.1.tgz \
       -o /tmp/docker.tgz \
    && tar -xzf /tmp/docker.tgz -C /tmp \
    && mv /tmp/docker/docker /usr/local/bin/docker \
    && rm -rf /tmp/docker.tgz /tmp/docker \
    && apt-get purge -y curl \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
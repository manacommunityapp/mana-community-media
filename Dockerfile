# ─────────────────────────────────────────────────────────────────────────────
# Mana Community Media Service — Dockerfile
# Multi-stage build: compile + package → minimal runtime image
# ─────────────────────────────────────────────────────────────────────────────

# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

# Download deps + build (skip tests — run separately in CI)
RUN apk add --no-cache maven && \
    mvn -q dependency:go-offline && \
    mvn -q package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine AS runtime

LABEL maintainer="Mana Community Team"
LABEL service="mana-community-media"

RUN addgroup -S media && adduser -S media -G media

WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

# Non-root execution
USER media

EXPOSE 8084

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]

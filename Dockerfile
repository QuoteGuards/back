# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# 의존성 캐시를 위해 wrapper/그래들 설정 파일만 먼저 복사
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew \
    && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 소스 복사 후 빌드 (테스트 제외 — 테스트는 CI 단계에서 별도 실행)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring \
    && mkdir -p /app/uploads \
    && chown -R spring:spring /app

COPY --from=build --chown=spring:spring /workspace/build/libs/*-SNAPSHOT.jar app.jar

USER spring:spring

EXPOSE 8080

# app.storage.local.dir=./uploads 기준 상대 경로와 일치 (WORKDIR=/app)
VOLUME ["/app/uploads"]

# JVM 옵션은 JAVA_TOOL_OPTIONS 환경변수로 주입 가능 (예: -Xmx512m)
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

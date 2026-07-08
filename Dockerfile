# syntax=docker/dockerfile:1
# ============================================
# [1단계] 빌드 스테이지 — jar 파일 생성
# ============================================
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

COPY src src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew clean bootJar --no-daemon --max-workers=1

# ============================================
# [2단계] 실행 스테이지 — jar만 가져와서 실행
# ============================================
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
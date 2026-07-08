# ============================================
# [1단계] 빌드 스테이지 — jar 파일 생성
# ============================================
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# 변하지 않는 것부터 복사 (캐시 재활용 → 빌드 속도 ↑)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

# 자주 바뀌는 소스코드는 마지막에 복사
COPY src src

# 1GB RAM 서버라 데몬/병렬 빌드 다 끄고 메모리 최소로 빌드
RUN ./gradlew clean bootJar --no-daemon --max-workers=1

# ============================================
# [2단계] 실행 스테이지 — jar만 가져와서 실행
# ============================================
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
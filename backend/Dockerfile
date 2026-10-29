FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
# Wrapper trên Windows có thể dùng CRLF; không sửa file trên máy người dùng.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon test bootJar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system speakmate && useradd --system --gid speakmate --home-dir /app speakmate
COPY --from=build --chown=speakmate:speakmate /workspace/build/libs/*-SNAPSHOT.jar ./app.jar
USER speakmate
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

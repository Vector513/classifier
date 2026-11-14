FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
ENV GRADLE_USER_HOME=/opt/gradle-cache
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY src src
# Keep downloaded dependencies and cacheable task outputs across source changes.
RUN --mount=type=cache,id=classifier-gradle,target=/opt/gradle-cache,sharing=locked \
    ./gradlew bootJar --no-daemon --build-cache

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

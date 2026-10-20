FROM --platform=$BUILDPLATFORM eclipse-temurin:21.0.12_8-jdk-noble AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon -q dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon -q bootJar -x test && cp build/libs/app.jar /src/app.jar

FROM eclipse-temurin:21.0.12_8-jre-noble
RUN useradd --system --uid 10001 clamon
USER 10001
COPY --from=build /src/app.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

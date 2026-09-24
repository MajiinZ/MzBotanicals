FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradle/ gradle/
COPY gradlew .
COPY server/ server/
RUN chmod +x gradlew && ./gradlew -p server test installDist --no-daemon

FROM eclipse-temurin:21-jre
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/server/build/install/server/ ./
USER 10001:10001
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]

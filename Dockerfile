# --- Build stage -------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# Warm the dependency cache in its own layer so source-only edits don't re-download the world.
RUN mvn -q -B dependency:go-offline || true
COPY src src
RUN mvn -q -B -DskipTests package

# --- Runtime stage -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S crm && adduser -S crm -G crm
COPY --from=build /build/target/crm-backend-*.jar app.jar
USER crm
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

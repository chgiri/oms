# ---- Build stage ------------------------------------------------------
# Use the project's own Maven wrapper (mvnw) rather than a Maven base image,
# so the build here always matches the version pinned in
# .mvn/wrapper/maven-wrapper.properties instead of whatever the CI/host has.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# Copy only what's needed to resolve dependencies first, so this layer is
# cached across builds unless pom.xml itself changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Now copy the actual source and build the jar. Tests are skipped here —
# they need Testcontainers (Docker-in-Docker) and a running Kafka/Postgres,
# which this build stage doesn't have; run `./mvnw test` in CI before this
# image is built, not as part of building it.
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ---- Runtime stage ------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# curl is needed by the HEALTHCHECK below; the base jre-jammy image doesn't
# include it. Do this as root, before switching to the non-root user.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as a non-root user rather than the image's default root.
RUN groupadd --system oms && useradd --system --gid oms oms
USER oms

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

# Hits the one endpoint SecurityConfig leaves unauthenticated for exactly
# this purpose (see /actuator/health permitAll + management.endpoint.health
# config in application.properties).
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD curl --fail http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

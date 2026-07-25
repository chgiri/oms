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

# Ties this specific image back to the exact commit and version it was built
# from — without this, every image looks identical from the outside and
# "which commit is running in prod?" has no answer short of guessing from
# deploy timestamps. Both default to "unknown" so a plain `docker build`
# (no --build-arg) still works for local dev; a real build/CI pipeline passes
# the real values in.
ARG GIT_SHA=unknown
ARG APP_VERSION=unknown

# OCI-standard labels — inspectable from outside the container without it
# even running: `docker inspect --format='{{index .Config.Labels "org.opencontainers.image.revision"}}' <image>`
LABEL org.opencontainers.image.revision=$GIT_SHA
LABEL org.opencontainers.image.version=$APP_VERSION

# Also surfaced from inside the running app, so /actuator/info can report
# them (see application.properties info.app.* and SecurityConfig permitAll).
ENV APP_GIT_SHA=$GIT_SHA
ENV APP_VERSION=$APP_VERSION

EXPOSE 8080

# Hits the one endpoint SecurityConfig leaves unauthenticated for exactly
# this purpose (see /actuator/health permitAll + management.endpoint.health
# config in application.properties).
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD curl --fail http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

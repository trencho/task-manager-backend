# syntax=docker/dockerfile:1

# Comments are provided throughout this file to help you get started.
# If you need more help, visit the Dockerfile reference guide at
# https://docs.docker.com/go/dockerfile-reference/

# Want to help us make this template better? Share your feedback here: https://forms.gle/ybq9Krt8jtBL3iCk7

################################################################################

# Create a stage for resolving and downloading dependencies.
FROM eclipse-temurin:25-jdk-jammy@sha256:0348e7b24ad4479cf35927b750671bb4b78465c303003b08536f6f2fa6f180cd AS deps

WORKDIR /build

# Copy the mvnw wrapper with executable permissions.
COPY --chmod=0755 mvnw mvnw
COPY .mvn/ .mvn/

# Download dependencies as a separate step to take advantage of Docker's caching.
# Leverage a cache mount to /root/.m2 so that subsequent builds don't have to
# re-download packages.
RUN --mount=type=bind,source=pom.xml,target=pom.xml \
    --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -DskipTests

################################################################################

# Create a stage for building the application based on the stage with downloaded dependencies.
# This Dockerfile is optimized for Java applications that output an uber jar, which includes
# all the dependencies needed to run your app inside a JVM. If your app doesn't output an uber
# jar and instead relies on an application server like Apache Tomcat, you'll need to update this
# stage with the correct filename of your package and update the base image of the "final" stage
# use the relevant app server, e.g., using tomcat (https://hub.docker.com/_/tomcat/) as a base image.
FROM deps AS package

WORKDIR /build

COPY ./src src/
RUN --mount=type=bind,source=pom.xml,target=pom.xml \
    --mount=type=cache,target=/root/.m2 \
    ./mvnw package -DskipTests && \
    mv target/$(./mvnw help:evaluate -Dexpression=project.artifactId -q -DforceStdout)-$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout).jar target/app.jar

################################################################################

# Create a stage for extracting the application into separate layers.
# Take advantage of Spring Boot's layer tools and Docker's caching by extracting
# the packaged application into separate layers that can be copied into the final stage.
# See Spring's docs for reference:
# https://docs.spring.io/spring-boot/docs/current/reference/html/container-images.html
FROM package AS extract

WORKDIR /build

# `-Djarmode=tools ... extract --layers --launcher`, not the old `-Djarmode=layertools`. Spring Boot
# deprecated layertools in 3.3 and REMOVED it in 4; this repo has been on Boot 4 since 86b8e6c, so
# the build died with `Unsupported jarmode 'layertools'` and had done since. Nothing in CI builds
# this image -- there is no docker build step and no deploy workflow -- so a Dockerfile that could
# not produce an image at all went unnoticed.
#
# The layer names are unchanged, verified by running the extract and listing the output: the four
# COPY lines in the final stage and the JarLauncher entrypoint below still apply as written.
RUN java -Djarmode=tools -jar target/app.jar extract --layers --launcher --destination target/extracted

################################################################################

# Create a new stage for running the application that contains the minimal
# runtime dependencies for the application. This often uses a different base
# image from the install or build stage where the necessary files are copied
# from the install stage.
#
# The example below uses eclipse-temurin's JRE image as the foundation for running the app.
# Both stages pin the digest as well as the tag: a bare "25-jre-jammy" resolves to whatever
# that tag points at on the day of the build, so two builds of the same commit can differ.
# The tag is kept alongside the digest only so a human can read which image this is.
# Dependabot proposes digest bumps, which is how these stay current.
FROM eclipse-temurin:25-jre-jammy@sha256:b8ba5fca9d88b6ecc3a46c8e75b744f84aca9a9d08587901b5ab480baf641ab5 AS final

# curl exists solely so the container can answer the HEALTHCHECK below. This base image ships NO
# HTTP client at all -- no curl, wget, nc or busybox, verified by running it -- and without one
# Docker can only observe the process EXITING, so a wedged-but-alive JVM would serve errors
# forever. Installed before the USER switch, because apt needs root.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Create a non-privileged user that the app will run under.
# See https://docs.docker.com/go/dockerfile-user-best-practices/
ARG UID=10001
RUN adduser \
    --disabled-password \
    --gecos "" \
    --home "/nonexistent" \
    --shell "/sbin/nologin" \
    --no-create-home \
    --uid "${UID}" \
    appuser
USER appuser

# Copy the executable from the "package" stage.
COPY --from=extract build/target/extracted/dependencies/ ./
COPY --from=extract build/target/extracted/spring-boot-loader/ ./
COPY --from=extract build/target/extracted/snapshot-dependencies/ ./
COPY --from=extract build/target/extracted/application/ ./

EXPOSE 80

# 127.0.0.1, never localhost: in a container `localhost` can resolve to ::1 first while the JVM
# binds IPv4, and a client that tries only the first address then probes an address nothing is
# listening on. A sibling repo shipped exactly that and reported `unhealthy` for two months.
#
# Readiness rather than the aggregate /actuator/health: readiness answers "can this serve traffic",
# which is the question a restart policy should act on, and it is one of the two paths
# SecurityConfig permits anonymously. The management port is 9090 and docker-compose does not
# publish it, so this probe is reachable from inside the container and nowhere else.
#
# start-period covers JVM boot plus the Mongo connection; failures before it elapses do not count.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS -o /dev/null http://127.0.0.1:9090/actuator/health/readiness || exit 1

ENTRYPOINT [ "java", "org.springframework.boot.loader.launch.JarLauncher" ]

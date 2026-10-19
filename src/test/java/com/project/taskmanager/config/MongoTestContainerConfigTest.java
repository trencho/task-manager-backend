package com.project.taskmanager.config;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers how the integration tests pick their Mongo image: from the production Dockerfile, so a
 * Dependabot bump of that file moves the tests with it.
 */
class MongoTestContainerConfigTest {

    @Test
    void readsTheImageTheProductionDockerfileRuns() throws IOException {
        final var dockerfile = Files.readString(MongoTestContainerConfig.MONGO_DOCKERFILE);

        final var image = MongoTestContainerConfig.productionMongoImage(dockerfile.lines().toList());

        assertThat(image).startsWith("mongo@sha256:");
        final var digest = image.substring(image.indexOf('@'));
        assertThat(dockerfile).containsPattern("(?im)^FROM\\s+mongo:[^\\s@]+" + Pattern.quote(digest) + "\\s*$");
    }

    @Test
    void takesTheLastStageAndPinsItsDigest() {
        final var lines = List.of("# syntax=docker/dockerfile:1", "FROM alpine:3.22 AS fetch", "RUN echo hi",
                "from\tmongo:8.0.32@sha256:abc123 AS db", "COPY init.js /docker-entrypoint-initdb.d/");

        assertThat(MongoTestContainerConfig.productionMongoImage(lines)).isEqualTo("mongo@sha256:abc123");
    }

    @Test
    void keepsARegistryPortWhileDroppingTheTag() {
        final var lines = List.of("FROM registry.local:5000/mongo:8.0.32@sha256:abc123");

        assertThat(MongoTestContainerConfig.productionMongoImage(lines))
                .isEqualTo("registry.local:5000/mongo@sha256:abc123");
    }

    @Test
    void skipsAPlatformFlag() {
        final var lines = List.of("FROM --platform=linux/amd64 mongo:8.0.32");

        assertThat(MongoTestContainerConfig.productionMongoImage(lines)).isEqualTo("mongo:8.0.32");
    }

    @Test
    void refusesABuildArgumentItCannotResolve() {
        assertThatThrownBy(() -> MongoTestContainerConfig.productionMongoImage(List.of("FROM mongo:${MONGO_TAG}")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot resolve the build argument");
    }

    @Test
    void refusesADockerfileWithNoFromLine() {
        assertThatThrownBy(() -> MongoTestContainerConfig.productionMongoImage(List.of("COPY a b")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("has no FROM line");
    }
}

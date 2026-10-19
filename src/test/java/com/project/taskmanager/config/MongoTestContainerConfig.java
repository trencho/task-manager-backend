package com.project.taskmanager.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.testcontainers.containers.MongoDBContainer;

@TestConfiguration
public class MongoTestContainerConfig {

    /**
     * Resolved against Maven's {@code basedir}, which Surefire sets, so a run from an IDE with
     * another working directory finds the same file.
     */
    static final Path MONGO_DOCKERFILE = Path.of(System.getProperty("basedir", "."), "docker", "mongo", "Dockerfile");

    private static final Pattern FROM_LINE = Pattern.compile("(?i)^FROM\\s+(.*)$");

    /**
     * The image the production compose stack runs: the last {@code FROM} line of
     * docker/mongo/Dockerfile, which is the stage that ships, digest included. Dependabot bumps only
     * that file and cannot see a tag in Java source, so a literal here drifted within a day of the
     * 8.0.28 -> 8.0.29 bump auto-merging (#13), and again from 8.0.29 to 8.0.32. Reading the
     * Dockerfile makes every Dependabot bump the version the integration tests run.
     * <p>
     * {@code --platform} style flags are skipped. A {@code ${...}} build argument cannot be resolved
     * here, so it fails with a message naming this method. When the line carries a digest the tag is
     * dropped ({@code mongo@sha256:...}): the digest names the exact image, and Testcontainers reads
     * {@code mongo:8.0.32@sha256:...} as a repository called {@code mongo:8.0.32} and refuses it.
     */
    static String productionMongoImage(final List<String> dockerfileLines) {
        String image = null;
        for (final var line : dockerfileLines) {
            final Matcher from = FROM_LINE.matcher(line.strip());
            if (from.matches()) {
                image = Arrays.stream(from.group(1).split("\\s+")).filter(token -> !token.startsWith("--")).findFirst()
                        .orElse(null);
            }
        }
        if (image == null) {
            throw new IllegalStateException(MONGO_DOCKERFILE + " has no FROM line naming an image");
        }
        if (image.contains("${") || image.startsWith("$")) {
            throw new IllegalStateException("productionMongoImage cannot resolve the build argument in '" + image
                    + "' from " + MONGO_DOCKERFILE);
        }
        final int at = image.indexOf('@');
        if (at < 0) {
            return image;
        }
        final var name = image.substring(0, at);
        final int tag = name.lastIndexOf(':');
        final var repository = tag > name.lastIndexOf('/') ? name.substring(0, tag) : name;
        return repository + image.substring(at);
    }

    @Bean
    public MongoDBContainer mongoDBContainer() throws IOException {
        final var mongoDBContainer = new MongoDBContainer(productionMongoImage(Files.readAllLines(MONGO_DOCKERFILE)));
        mongoDBContainer.start();
        return mongoDBContainer;
    }

    @Bean
    public MongoTemplate mongoTemplate(final MongoDBContainer mongoDBContainer) {
        final var mongoUri = String.format("mongodb://%s:%d/task-manager", mongoDBContainer.getHost(),
                mongoDBContainer.getMappedPort(27017));

        final var connectionString = new ConnectionString(mongoUri);
        final var mongoClientSettings = MongoClientSettings.builder().applyConnectionString(connectionString).build();

        return new MongoTemplate(
                new SimpleMongoClientDatabaseFactory(MongoClients.create(mongoClientSettings), "task-manager"));
    }
}

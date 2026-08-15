package com.project.taskmanager.config;

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
     * Pinned to the same image the production compose stack runs (docker/mongo/Dockerfile is
     * {@code mongo:8.0.28}). {@code mongo:latest} used to be used here, which contradicted the
     * repository's own stated policy -- .github/dependabot.yml holds the mongo image to the 8.0
     * line because "8.1+ are rapid releases" -- and meant the integration tests could silently
     * start exercising a different major than the one that ships.
     */
    @Bean
    public MongoDBContainer mongoDBContainer() {
        final var mongoDBContainer = new MongoDBContainer("mongo:8.0.28");
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

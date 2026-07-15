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

    @Bean
    public MongoDBContainer mongoDBContainer() {
        final var mongoDBContainer = new MongoDBContainer("mongo:latest");
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

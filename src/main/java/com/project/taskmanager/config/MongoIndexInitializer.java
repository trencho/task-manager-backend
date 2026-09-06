package com.project.taskmanager.config;

import java.time.Duration;
import jakarta.annotation.PostConstruct;

import com.project.taskmanager.entity.RefreshToken;
import com.project.taskmanager.entity.Task;
import com.project.taskmanager.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Creates every index the application depends on, at startup, from one place.
 *
 * <p>The mechanism is deliberate. {@code User.username} carried an {@code @Indexed} annotation that
 * created <em>no index at all</em>: Spring Data MongoDB has defaulted
 * {@code spring.data.mongodb.auto-index-creation} to false since 3.0, and that key appears nowhere
 * in this repository. Adding {@code unique = true} to that annotation would have created nothing
 * either — the compounding trap, where the fix reads as a constraint and enforces nothing.
 *
 * <p>Three mechanisms were available and this is the one that can be proved. Turning
 * auto-index-creation on hides the definitions across entity classes and gives no place to express
 * a compound or TTL index cleanly. {@code docker/mongo/init/init-mongo.js} runs only on the first
 * start of an empty data directory, so it reaches neither an existing deployment nor a
 * Testcontainers instance, which is the same as being untestable. This component runs wherever the
 * application runs, and {@code MongoIndexIntegrationTest} reads the indexes back out of a real
 * Mongo rather than reading an annotation.
 *
 * <p>Creation is idempotent: Mongo accepts a {@code createIndex} for an index that already exists
 * with the same specification, and rejects one whose name matches a different specification. So a
 * changed definition here fails loudly at startup rather than being silently ignored.
 */
@Component
@RequiredArgsConstructor
public class MongoIndexInitializer {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        // The guarantee behind registration. UserServiceImpl checks for an existing username first,
        // but check-then-save is a race: two concurrent signups both read "absent" and both write.
        // Only the database can settle that, and this is what makes it settle it.
        mongoTemplate.indexOps(User.class)
                .createIndex(new Index().on("username", Sort.Direction.ASC).unique().named("users_username_unique"));

        // Every task read filters on username and the common sorts are by dueDate, so the compound
        // index serves both halves of one query. Field order matters: the equality predicate leads,
        // the range/sort field follows.
        mongoTemplate.indexOps(Task.class).createIndex(new Index().on("username", Sort.Direction.ASC)
                .on("dueDate", Sort.Direction.ASC).named("tasks_username_dueDate"));

        // A refresh token is deleted when it is presented and found expired. A user who never comes
        // back never presents one, so without this the row lives forever. expireAfter(ZERO) means
        // "delete once expiryDate has passed" rather than "delete immediately".
        mongoTemplate.indexOps(RefreshToken.class).createIndex(new Index().on("expiryDate", Sort.Direction.ASC)
                .expire(Duration.ZERO).named("refresh_tokens_expiryDate_ttl"));
    }
}

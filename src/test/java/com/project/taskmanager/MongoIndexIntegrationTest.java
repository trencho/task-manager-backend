package com.project.taskmanager;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.project.taskmanager.config.MongoIndexInitializer;
import com.project.taskmanager.config.MongoTestContainerConfig;
import com.project.taskmanager.entity.RefreshToken;
import com.project.taskmanager.entity.Task;
import com.project.taskmanager.entity.User;
import com.project.taskmanager.repository.UserRepository;
import com.project.taskmanager.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reads the indexes back out of a real Mongo. Every assertion here asks the running server what it
 * actually has, because the defect this closes is an annotation that reads as an index and creates
 * none — a difference no amount of reading Java can show.
 */
@ContextConfiguration(classes = MongoTestContainerConfig.class)
@SpringBootTest
class MongoIndexIntegrationTest {

    private static final String USERNAME = "index-test-user";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    private static User newUser() {
        return User.builder().username(USERNAME).email("user@mail.com").password("raw-password").build();
    }

    private static boolean succeeded(final Future<Boolean> attempt) {
        try {
            return attempt.get();
        } catch (Exception e) {
            throw new IllegalStateException("a registration attempt did not complete", e);
        }
    }

    private List<IndexInfo> indexesOf(final Class<?> entity) {
        return mongoTemplate.indexOps(entity).getIndexInfo();
    }

    private List<List<String>> indexKeysOf(final Class<?> entity) {
        return indexesOf(entity).stream().map(index -> index.getIndexFields().stream().map(IndexField::getKey).toList())
                .toList();
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void theUsernameIndexExistsAndIsUnique() {
        final var usernameIndex = indexesOf(User.class).stream()
                .filter(index -> index.getIndexFields().stream().anyMatch(field -> "username".equals(field.getKey())))
                .findFirst();

        assertThat(usernameIndex).as("users.username must carry an index").isPresent();
        assertThat(usernameIndex.get().isUnique()).as("a non-unique index gives registration no guarantee at all")
                .isTrue();
    }

    /**
     * The database refuses the second write, not the application. Asserted by going around
     * {@link UserService} entirely: its own duplicate lookup would answer first and prove nothing
     * about the index.
     */
    @Test
    void mongoRefusesASecondUserWithTheSameUsername() {
        userRepository.save(newUser());

        assertThatThrownBy(() -> userRepository.save(newUser())).isInstanceOf(DuplicateKeyException.class);

        assertThat(userRepository.findAll()).hasSize(1);
    }

    /**
     * The race the unique index exists for. Both callers start together, so both can clear
     * {@code registerUser}'s check-then-save lookup before either writes — and one of them then has
     * to lose at the database.
     */
    @Test
    void twoConcurrentRegistrationsProduceExactlyOneUser() {
        final var barrier = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            final var attempts = List.of(register(executor, barrier), register(executor, barrier));

            assertThat(attempts.stream().filter(MongoIndexIntegrationTest::succeeded).count())
                    .as("exactly one of two concurrent registrations may succeed").isEqualTo(1);
        }

        assertThat(userRepository.findAll()).hasSize(1);
    }

    private Future<Boolean> register(final ExecutorService executor, final CyclicBarrier barrier) {
        return executor.submit(() -> {
            barrier.await();
            try {
                userService.registerUser(newUser());
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        });
    }

    /**
     * Field ORDER is the point: {@code {dueDate, username}} would not serve the equality predicate
     * every task read leads with, so asserting "an index mentioning both" would pass on the wrong
     * index.
     */
    @Test
    void theTaskQueryPathIsIndexedOnUsernameThenDueDate() {
        assertThat(indexKeysOf(Task.class)).as("tasks must carry a compound index the owner-scoped query can use")
                .contains(List.of("username", "dueDate"));
    }

    @Test
    void refreshTokensExpireThemselves() {
        final var ttlIndex = indexesOf(RefreshToken.class).stream()
                .filter(index -> index.getIndexFields().stream().anyMatch(field -> "expiryDate".equals(field.getKey())))
                .findFirst();

        assertThat(ttlIndex).as("refresh_tokens.expiryDate must carry an index").isPresent();
        // An expired token is deleted when it is presented. A user who never comes back never
        // presents one, so without a TTL nothing else ever removes the row.
        assertThat(ttlIndex.get().getExpireAfter()).as("a plain index here would leave every abandoned token forever")
                .contains(Duration.ZERO);
    }

    /**
     * The initializer claims to be idempotent, and every restart re-runs it. Driving it a second
     * time against a database that already has the indexes is what turns that claim into a fact:
     * a respecification Mongo disagrees with is an error, and it would otherwise surface as a
     * deploy that will not start.
     */
    @Test
    void reCreatingTheIndexesChangesNothing() {
        final var before = indexNames();
        // Asserted before the second run, so this test cannot be the thing that created them. It
        // runs in an arbitrary position among its siblings, and without this line a suite whose
        // ordering put it first would create the indexes for every other assertion here -- the
        // whole file passing while @PostConstruct had been deleted.
        assertThat(before).contains("users_username_unique", "tasks_username_dueDate", "refresh_tokens_expiryDate_ttl");

        new MongoIndexInitializer(mongoTemplate).createIndexes();

        assertThat(indexNames()).isEqualTo(before);
    }

    private List<String> indexNames() {
        return List.of(User.class, Task.class, RefreshToken.class).stream()
                .flatMap(entity -> indexesOf(entity).stream().map(IndexInfo::getName)).sorted().toList();
    }
}

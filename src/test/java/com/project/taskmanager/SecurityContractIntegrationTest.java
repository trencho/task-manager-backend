package com.project.taskmanager;

import com.project.taskmanager.config.MockMvcSecurityConfig;
import com.project.taskmanager.config.MongoTestContainerConfig;
import com.project.taskmanager.entity.User;
import com.project.taskmanager.repository.UserRepository;
import com.project.taskmanager.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two security properties every other test assumed and none asserted.
 *
 * <p>Both were found by mutation, and both let a catastrophe ship on a green build:
 *
 * <ul>
 * <li>Changing {@code .anyRequest().authenticated()} to {@code .permitAll()} in
 * {@code SecurityConfig} passed all 128 tests. The entire task API becomes anonymous. It survived
 * because all 36 {@code TaskControllerIntegrationTest} methods carry {@code @WithMockUser}, and the
 * only 401 assertions in the suite were against {@code /api/auth/logout-all} -- which has its own
 * matcher ordered ahead of the mutated one, so it kept passing and gave false comfort.
 * <li>Swapping {@code BCryptPasswordEncoder} for {@code NoOpPasswordEncoder} passed all 128 tests,
 * storing every password in Mongo as plaintext while login kept working. It survived because the
 * one test that looks like it covers this captures what {@code save()} was handed and asserts it
 * equals {@code "hashed"} -- a value the test's own stub returned. A dependency a test supplies
 * itself is a dependency that test cannot check the wiring of.
 * </ul>
 *
 * <p>Both run against the real container and the real security chain, because that is the only
 * place either property exists.
 */
@AutoConfigureMockMvc
@ContextConfiguration(classes = MongoTestContainerConfig.class)
@SpringBootTest
@org.springframework.context.annotation.Import(MockMvcSecurityConfig.class)
class SecurityContractIntegrationTest {

    private static final String RAW_PASSWORD = "a-password-worth-hashing";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void clear() {
        userRepository.deleteAll();
    }

    @Test
    void shouldRejectAnAnonymousCallerOnTheTaskApi() throws Exception {
        mockMvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAnAnonymousCallerOnASpecificTask() throws Exception {
        // A second route, because the mutation that motivated this test changes a single
        // `anyRequest()` rule: one endpoint passing could be a route-specific matcher rather than
        // the catch-all actually being enforced.
        mockMvc.perform(get("/api/tasks/000000000000000000000000")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldStoreThePasswordHashedRatherThanAsGiven() {
        userService.registerUser(User.builder()
                .username("hashing-contract")
                .email("hashing-contract@example.com")
                .password(RAW_PASSWORD)
                .build());

        // Read it back out of the container. Asserting on what the service handed a mock would
        // prove only that the test's own stub returned what the test told it to.
        final var stored = userRepository.findByUsername("hashing-contract").orElseThrow();

        assertThat(stored.getPassword()).as("the raw password must never reach the database")
                .isNotEqualTo(RAW_PASSWORD);
        assertThat(stored.getPassword()).as("BCrypt hashes carry a $2a$/$2b$/$2y$ prefix and a cost factor")
                .matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }
}

package com.project.taskmanager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.project.taskmanager.config.MongoTestContainerConfig;
import com.project.taskmanager.entity.Task;
import com.project.taskmanager.entity.User;
import com.project.taskmanager.enums.Priority;
import com.project.taskmanager.enums.TaskStatus;
import com.project.taskmanager.repository.TaskRepository;
import com.project.taskmanager.repository.UserRepository;
import com.project.taskmanager.security.CustomUserDetails;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@ContextConfiguration(classes = MongoTestContainerConfig.class)
@SpringBootTest
class TaskControllerIntegrationTest {

    private static final String BASE_URL = "/api/tasks";
    private static final String TASK_JSON =
            "{\"title\": \"New Task Title\", \"description\": \"New Task Description\"}";
    private static final String UPDATED_TASK_JSON =
            "{\"title\": \"Updated Task Title\", \"description\": \"Updated Task Description\"}";
    private static final String USERNAME = "username";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    private Task task;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        userRepository.deleteAll();

        final var user = new User();
        user.setUsername("username");
        user.setPassword("password");
        userRepository.save(user);

        task = new Task();
        task.setTitle("Initial Task Title");
        task.setDescription("Initial Task Description");
        task.setDueDate(LocalDate.now());
        // Deliberately not PENDING: PENDING is the create-time default, so a seeded PENDING
        // could not tell "preserved the existing status" apart from "fell back to the default".
        task.setStatus(TaskStatus.IN_PROGRESS);
        // Same reasoning: MEDIUM is the create-time default for priority.
        task.setPriority(Priority.HIGH);
        task.setUsername(USERNAME);
        taskRepository.save(task);

        final var authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(user));

        final var securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);
    }

    /**
     * Seeds a second task for the same user, plus one belonging to somebody else. The other user's
     * task must never appear in any filtered result — scoping is not a filter, it is an invariant.
     */
    private void seedForSearch() {
        final var groceries =
                new Task("Buy groceries", "Milk and BREAD", LocalDate.now().plusDays(2), TaskStatus.PENDING, USERNAME);
        groceries.setPriority(Priority.LOW);
        taskRepository.save(groceries);

        // MEDIUM, not HIGH: the task seeded in setUp() is already HIGH, so filtering on HIGH here
        // would match two tasks and the assertion would say nothing about the filter.
        final var report = new Task(
                "Write report", "Quarterly numbers", LocalDate.now().plusDays(30), TaskStatus.COMPLETED, USERNAME);
        report.setPriority(Priority.MEDIUM);
        taskRepository.save(report);

        final var foreign =
                new Task("Buy groceries", "Someone else's list", LocalDate.now(), TaskStatus.PENDING, "otheruser");
        foreign.setPriority(Priority.LOW);
        taskRepository.save(foreign);
    }

    @Test
    @WithMockUser(username = USERNAME)
    void shouldFilterByStatus() throws Exception {
        seedForSearch();

        mockMvc.perform(get(BASE_URL).param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Write report"));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void shouldFilterByPriority() throws Exception {
        seedForSearch();

        // MEDIUM matches only "Write report": setUp()'s task is HIGH, groceries is LOW.
        mockMvc.perform(get(BASE_URL).param("priority", "MEDIUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Write report"));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void shouldSearchTheTitleCaseInsensitively() throws Exception {
        seedForSearch();

        mockMvc.perform(get(BASE_URL).param("q", "GROCERIES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Buy groceries"));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void shouldSearchTheDescriptionCaseInsensitively() throws Exception {
        seedForSearch();

        mockMvc.perform(get(BASE_URL).param("q", "bread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Buy groceries"));
    }

    /**
     * `q` goes into a regex. Unquoted, ".*" would match every task — and a pathological pattern
     * would hang the server. Pattern.quote makes it a literal.
     */
    @Test
    @WithMockUser(username = USERNAME)
    void shouldTreatRegexMetacharactersInTheQueryAsLiterals() throws Exception {
        seedForSearch();

        mockMvc.perform(get(BASE_URL).param("q", ".*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void shouldFilterByDueBeforeExclusively() throws Exception {
        seedForSearch();

        // The seeded task is due today; groceries in 2 days; the report in 30.
        mockMvc.perform(get(BASE_URL)
                        .param("dueBefore", LocalDate.now().plusDays(3).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void shouldCombineFilters() throws Exception {
        seedForSearch();

        mockMvc.perform(get(BASE_URL)
                        .param("q", "groceries")
                        .param("priority", "LOW")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Buy groceries"));
    }

    /**
     * The other user also has a task titled "Buy groceries". Filtering must never reach it.
     */
    @Test
    @WithMockUser(username = USERNAME)
    void shouldNeverReturnAnotherUsersTaskHoweverItIsFiltered() throws Exception {
        seedForSearch();

        mockMvc.perform(get(BASE_URL).param("q", "groceries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Milk and BREAD"));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void shouldSortByDueDate() throws Exception {
        seedForSearch();

        mockMvc.perform(get(BASE_URL).param("sort", "dueDate,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Write report"));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void testGetAllTasks() throws Exception {
        final var task1 = new Task("Task 1", "Description 1", LocalDate.now(), TaskStatus.PENDING, "username1");
        taskRepository.save(task1);

        mockMvc.perform(get(BASE_URL).param("page", "0").param("size", "10").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Initial Task Title"))
                .andExpect(jsonPath("$.content[0].description").value("Initial Task Description"))
                // The list endpoint used to serialise the Task entity, so every item carried the
                // owner's username. The client needs `id` (it edits and deletes by it) and has no
                // business knowing the owner -- it can only ever see its own tasks.
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].username").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.number").value(0));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void testCreateTask() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(TASK_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New Task Title"))
                .andExpect(jsonPath("$.description").value("New Task Description"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @WithMockUser(username = USERNAME)
    void shouldDefaultToPendingWhenNoStatusIsSupplied() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(TASK_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    /**
     * The client's status used to be discarded: createTask overwrote it with PENDING even though
     * TaskDTO exposes the field, so a task could never be created in any other state.
     */
    @Test
    @WithMockUser(username = USERNAME)
    void shouldHonourTheStatusSuppliedOnCreate() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Started Task\", \"description\": \"d\", \"status\": \"IN_PROGRESS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    /**
     * A PUT that omits `status` used to null the field out, because updateTask copied the
     * incoming value unconditionally. UPDATED_TASK_JSON omits it, and no assertion caught it.
     */
    @Test
    @WithMockUser(username = USERNAME)
    void shouldPreserveTheExistingStatusWhenUpdateOmitsIt() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATED_TASK_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(task.getStatus().name()));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void shouldDefaultToMediumWhenNoPriorityIsSupplied() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(TASK_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void shouldHonourThePrioritySuppliedOnCreate() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Urgent Task\", \"description\": \"d\", \"priority\": \"HIGH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    /**
     * A PUT that omits `priority` must not null the field — the same trap that `status` fell into.
     */
    @Test
    @WithMockUser(username = USERNAME)
    void shouldPreserveTheExistingPriorityWhenUpdateOmitsIt() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATED_TASK_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value(task.getPriority().name()));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void shouldApplyANewPriorityOnUpdate() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Updated Task Title\", \"description\": \"d\", \"priority\": \"LOW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("LOW"));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void shouldApplyANewStatusOnUpdate() throws Exception {
        mockMvc.perform(
                        put(BASE_URL + "/{id}", task.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"title\": \"Updated Task Title\", \"description\": \"d\", \"status\": \"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void testGetTaskSuccessful() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{id}", task.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(task.getTitle()))
                .andExpect(jsonPath("$.description").value(task.getDescription()));
    }

    @Test
    @WithMockUser(username = "username1")
    void testGetTaskFailed() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{id}", task.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = USERNAME)
    void testUpdateTaskSuccessful() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATED_TASK_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Task Title"))
                .andExpect(jsonPath("$.description").value("Updated Task Description"));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void testUpdateTaskFailedMissingTask() throws Exception {
        mockMvc.perform(put(BASE_URL + "/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATED_TASK_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Task not found with id: 100"));
    }

    @Test
    @WithMockUser(username = "username1")
    void testUpdateTaskFailedIncorrectUser() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATED_TASK_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Task not found for user: username1"));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void testDeleteTaskSuccessful() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{id}", task.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = USERNAME)
    void testDeleteTaskFailedMissingTask() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/100").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Task not found with id: 100"));
    }

    @Test
    @WithMockUser(username = "username1")
    void testDeleteTaskFailedIncorrectUser() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{id}", task.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Task not found for user: username1"));
    }
}

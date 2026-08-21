package com.project.taskmanager.entity;

import java.time.LocalDate;
import java.util.Set;

import com.project.taskmanager.enums.Priority;
import com.project.taskmanager.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    private String id;

    private String title;
    private String description;
    private LocalDate dueDate;
    private TaskStatus status;
    private Priority priority;
    private String username;

    /**
     * Free-form labels. A {@link Set} rather than a list: a tag applied twice to one task means the same
     * thing as once, and the filter would otherwise have to de-duplicate on every read.
     */
    private Set<String> tags;

    public Task(final String title, final String description, final LocalDate dueDate, final TaskStatus status,
            final String username) {
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.status = status;
        this.username = username;
    }
}

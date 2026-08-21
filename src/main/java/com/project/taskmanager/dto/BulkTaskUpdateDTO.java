package com.project.taskmanager.dto;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import com.project.taskmanager.enums.Priority;
import com.project.taskmanager.enums.TaskStatus;

/**
 * Request body for the bulk update: which tasks, and what to set on them.
 *
 * <p>{@code status} and {@code priority} are both optional and a null means "leave alone", matching
 * the single-task update. Sending neither is accepted and changes nothing — the response count says
 * so, which is more useful to a client than a 400 for a request that is merely pointless.
 *
 * <p>The id list is capped. It is bounded by what a user can select in a list UI, and an uncapped
 * batch is a cheap way for one request to load every task in the database.
 */
public record BulkTaskUpdateDTO(
        @NotEmpty(message = "At least one task id is required") @Size(max = 100, message = "At most 100 tasks can be updated in one call") List<String> ids,
        TaskStatus status, Priority priority) {
}

package com.project.taskmanager.dto;

/**
 * Result of a bulk update.
 *
 * <p>A record rather than a bare number so the field is named in the JSON — a response body of
 * {@code 3} tells a client nothing about what the three counts.
 *
 * @param updated how many tasks were actually changed; less than the ids sent when some were not
 *                the caller's or no longer exist
 */
public record BulkUpdateResultDTO(int updated) {
}

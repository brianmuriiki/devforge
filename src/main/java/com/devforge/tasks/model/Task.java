package com.devforge.tasks.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * A unit of work. Immutable; use the {@code with...} methods to derive changed copies.
 *
 * <p>{@code dueDate} and {@code completedAt} may be null. Invariant: {@code completedAt} is set
 * exactly when the status is {@link TaskStatus#DONE} (the database enforces the same rule).
 */
public record Task(
        long id,
        long projectId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    public Task {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if ((status == TaskStatus.DONE) != (completedAt != null)) {
            throw new IllegalArgumentException("completedAt must be set exactly when status is DONE");
        }
    }

    /** True if the task has a due date before {@code today} and is not finished. */
    public boolean isOverdue(LocalDate today) {
        return dueDate != null && status != TaskStatus.DONE && dueDate.isBefore(today);
    }

    /**
     * Returns a copy with a new status. Entering DONE records the completion time (keeping the
     * original one if the task was already DONE); leaving DONE clears it.
     */
    public Task withStatus(TaskStatus newStatus, Instant now) {
        Instant stamp = now.truncatedTo(ChronoUnit.SECONDS);
        Instant newCompletedAt =
                newStatus == TaskStatus.DONE ? (completedAt != null ? completedAt : stamp) : null;
        return new Task(id, projectId, title, description, newStatus, priority, dueDate,
                createdAt, stamp, newCompletedAt);
    }

    /** Returns a copy with new editable details. Pass a null {@code newDueDate} to clear it. */
    public Task withDetails(
            String newTitle,
            String newDescription,
            TaskPriority newPriority,
            LocalDate newDueDate,
            Instant now) {
        Instant stamp = now.truncatedTo(ChronoUnit.SECONDS);
        return new Task(id, projectId, newTitle, newDescription, status, newPriority, newDueDate,
                createdAt, stamp, completedAt);
    }
}

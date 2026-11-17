package com.devforge.tasks.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** A recorded defect. Immutable; use the {@code with...} methods to derive changed copies. */
public record Bug(
        long id,
        long projectId,
        String title,
        String description,
        BugSeverity severity,
        BugStatus status,
        String reproductionSteps,
        Instant createdAt,
        Instant updatedAt) {

    public Bug {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reproductionSteps, "reproductionSteps");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public Bug withStatus(BugStatus newStatus, Instant now) {
        return new Bug(id, projectId, title, description, severity, newStatus, reproductionSteps,
                createdAt, now.truncatedTo(ChronoUnit.SECONDS));
    }

    public Bug withDetails(
            String newTitle,
            String newDescription,
            BugSeverity newSeverity,
            String newReproductionSteps,
            Instant now) {
        return new Bug(id, projectId, newTitle, newDescription, newSeverity, status,
                newReproductionSteps, createdAt, now.truncatedTo(ChronoUnit.SECONDS));
    }
}

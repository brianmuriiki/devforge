package com.devforge.tasks.service;

import com.devforge.tasks.model.BugSeverity;
import com.devforge.tasks.model.BugStatus;

/** Requested changes to a bug; a null field means "leave unchanged". */
public record BugChanges(
        String title,
        String description,
        BugSeverity severity,
        BugStatus status,
        String reproductionSteps) {

    public boolean isEmpty() {
        return title == null && description == null && severity == null && status == null
                && reproductionSteps == null;
    }
}

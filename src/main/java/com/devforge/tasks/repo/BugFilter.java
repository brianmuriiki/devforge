package com.devforge.tasks.repo;

import com.devforge.tasks.model.BugSeverity;
import com.devforge.tasks.model.BugStatus;

/** Optional criteria for listing bugs; a null field means "do not filter on this". */
public record BugFilter(Long projectId, BugStatus status, BugSeverity severity) {

    public static BugFilter none() {
        return new BugFilter(null, null, null);
    }
}

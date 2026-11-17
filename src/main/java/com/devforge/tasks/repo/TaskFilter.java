package com.devforge.tasks.repo;

import com.devforge.tasks.model.TaskPriority;
import com.devforge.tasks.model.TaskStatus;

/** Optional criteria for listing tasks; a null field means "do not filter on this". */
public record TaskFilter(Long projectId, TaskStatus status, TaskPriority priority) {

    public static TaskFilter none() {
        return new TaskFilter(null, null, null);
    }
}

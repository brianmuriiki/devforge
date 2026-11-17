package com.devforge.tasks.service;

import com.devforge.tasks.model.TaskPriority;
import com.devforge.tasks.model.TaskStatus;
import java.time.LocalDate;

/**
 * Requested changes to a task; a null field means "leave unchanged". Use
 * {@code clearDueDate} to remove the due date.
 */
public record TaskChanges(
        String title,
        String description,
        TaskPriority priority,
        TaskStatus status,
        LocalDate dueDate,
        boolean clearDueDate) {

    public TaskChanges {
        if (dueDate != null && clearDueDate) {
            throw new IllegalArgumentException("dueDate and clearDueDate are mutually exclusive");
        }
    }

    public boolean isEmpty() {
        return title == null && description == null && priority == null && status == null
                && dueDate == null && !clearDueDate;
    }
}

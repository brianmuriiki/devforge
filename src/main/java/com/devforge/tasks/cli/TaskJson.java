package com.devforge.tasks.cli;

import com.devforge.tasks.model.Task;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** The JSON shape of a task (part of the CLI's output contract). */
final class TaskJson {

    private TaskJson() {}

    static Map<String, Object> task(Task t, LocalDate today) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id());
        m.put("projectId", t.projectId());
        m.put("title", t.title());
        m.put("description", t.description());
        m.put("status", t.status().name());
        m.put("priority", t.priority().name());
        m.put("dueDate", t.dueDate() == null ? null : t.dueDate().toString());
        m.put("overdue", t.isOverdue(today));
        m.put("createdAt", t.createdAt().toString());
        m.put("updatedAt", t.updatedAt().toString());
        m.put("completedAt", t.completedAt() == null ? null : t.completedAt().toString());
        return m;
    }
}

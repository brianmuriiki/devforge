package com.devforge.tasks.cli;

import com.devforge.tasks.model.Bug;
import java.util.LinkedHashMap;
import java.util.Map;

/** The JSON shape of a bug (part of the CLI's output contract). */
final class BugJson {

    private BugJson() {}

    static Map<String, Object> bug(Bug b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.id());
        m.put("projectId", b.projectId());
        m.put("title", b.title());
        m.put("description", b.description());
        m.put("severity", b.severity().name());
        m.put("status", b.status().name());
        m.put("reproductionSteps", b.reproductionSteps());
        m.put("createdAt", b.createdAt().toString());
        m.put("updatedAt", b.updatedAt().toString());
        return m;
    }
}

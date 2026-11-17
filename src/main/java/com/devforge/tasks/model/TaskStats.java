package com.devforge.tasks.model;

import java.util.Map;

/** Progress summary for a set of tasks. {@code completionPercent} is rounded to one decimal. */
public record TaskStats(
        int total,
        int todo,
        int inProgress,
        int blocked,
        int done,
        int overdue,
        double completionPercent,
        Map<TaskPriority, Integer> byPriority) {}

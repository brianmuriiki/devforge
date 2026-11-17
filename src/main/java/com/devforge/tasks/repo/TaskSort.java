package com.devforge.tasks.repo;

public enum TaskSort {
    /** Oldest first. */
    ID,
    /** Earliest due date first; tasks without a due date come last. */
    DUE_DATE
}

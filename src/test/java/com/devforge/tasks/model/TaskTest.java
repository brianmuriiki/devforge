package com.devforge.tasks.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TaskTest {

    private static final Instant T0 = Instant.parse("2026-11-13T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-14T10:30:15.789Z");

    private static Task todo(LocalDate due) {
        return new Task(1, 1, "Write tests", "", TaskStatus.TODO, TaskPriority.MEDIUM, due, T0, T0, null);
    }

    @Test
    void completingRecordsTimestampAndKeepsItWhenRepeated() {
        Task done = todo(null).withStatus(TaskStatus.DONE, T1);
        assertEquals(TaskStatus.DONE, done.status());
        assertEquals(Instant.parse("2026-11-14T10:30:15Z"), done.completedAt()); // truncated to seconds
        assertEquals(done.completedAt(), done.updatedAt());

        Task again = done.withStatus(TaskStatus.DONE, T1.plusSeconds(3600));
        assertEquals(done.completedAt(), again.completedAt());
    }

    @Test
    void reopeningClearsCompletion() {
        Task reopened = todo(null).withStatus(TaskStatus.DONE, T1).withStatus(TaskStatus.TODO, T1.plusSeconds(60));
        assertEquals(TaskStatus.TODO, reopened.status());
        assertNull(reopened.completedAt());
    }

    @Test
    void withDetailsChangesFieldsButNotStatusOrCreationTime() {
        Task t = todo(null).withStatus(TaskStatus.BLOCKED, T1);
        Task changed = t.withDetails("New title", "notes", TaskPriority.CRITICAL, LocalDate.parse("2026-12-01"), T1);
        assertEquals("New title", changed.title());
        assertEquals(TaskPriority.CRITICAL, changed.priority());
        assertEquals(LocalDate.parse("2026-12-01"), changed.dueDate());
        assertEquals(TaskStatus.BLOCKED, changed.status());
        assertEquals(T0, changed.createdAt());
    }

    @Test
    void overdueRequiresPastDueDateAndUnfinishedStatus() {
        LocalDate today = LocalDate.parse("2026-11-13");
        assertTrue(todo(LocalDate.parse("2026-11-12")).isOverdue(today));
        assertFalse(todo(LocalDate.parse("2026-11-13")).isOverdue(today)); // due today is not overdue
        assertFalse(todo(null).isOverdue(today));
        assertFalse(todo(LocalDate.parse("2026-11-12")).withStatus(TaskStatus.DONE, T1).isOverdue(today));
    }

    @Test
    void invariantRejectsDoneWithoutCompletionTime() {
        assertThrows(IllegalArgumentException.class,
                () -> new Task(1, 1, "x", "", TaskStatus.DONE, TaskPriority.LOW, null, T0, T0, null));
    }

    @Test
    void invariantRejectsCompletionTimeOnUnfinishedTask() {
        assertThrows(IllegalArgumentException.class,
                () -> new Task(1, 1, "x", "", TaskStatus.TODO, TaskPriority.LOW, null, T0, T0, T0));
    }
}

package com.devforge.tasks.service;

import com.devforge.core.db.ConnectionProvider;
import com.devforge.core.db.Transactions;
import com.devforge.core.project.ActivityLogRepository;
import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectRegistry;
import com.devforge.core.project.ProjectStatus;
import com.devforge.tasks.model.Task;
import com.devforge.tasks.model.TaskStats;
import com.devforge.tasks.model.TaskPriority;
import com.devforge.tasks.model.TaskStatus;
import com.devforge.tasks.repo.TaskFilter;
import com.devforge.tasks.repo.TaskRepository;
import com.devforge.tasks.repo.TaskSort;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * TaskForge business rules: validation, state changes, transactions and activity logging.
 * Knows nothing about the command line.
 */
public final class TaskService {

    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_DESCRIPTION_LENGTH = 10_000;
    private static final String MODULE = "tasks";

    private final Transactions tx;
    private final ProjectRegistry projects;
    private final Clock clock;
    private final ZoneId zone;
    private final TaskRepository tasks = new TaskRepository();
    private final ActivityLogRepository activity = new ActivityLogRepository();

    /**
     * @param zone the user's time zone, used only to decide what "today" is for overdue checks
     */
    public TaskService(ConnectionProvider provider, ProjectRegistry projects, Clock clock, ZoneId zone) {
        this.tx = new Transactions(Objects.requireNonNull(provider, "provider"));
        this.projects = Objects.requireNonNull(projects, "projects");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /** Today's date in the configured zone. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    /** Parses {@code YYYY-MM-DD}. */
    public static LocalDate parseDueDate(String text) {
        LocalDate date;
        try {
            date = LocalDate.parse(text.strip());
        } catch (DateTimeParseException e) {
            throw invalidDate(text);
        }
        if (date.getYear() < 1000 || date.getYear() > 9999) {
            throw invalidDate(text);
        }
        return date;
    }

    private static TaskException invalidDate(String text) {
        return new TaskException(
                "Invalid date '" + text + "'. Use the format YYYY-MM-DD, for example 2026-12-31.");
    }

    /**
     * Resolves the project a command applies to: the given reference (id or name), or else the
     * registered project containing {@code workingDirectory}.
     */
    public Project resolveProject(String reference, Path workingDirectory) {
        if (reference != null) {
            return projects.resolve(reference);
        }
        return projects.findContaining(workingDirectory).orElseThrow(() -> new TaskException(
                "No project specified. Use --project <id|name>, or run this command inside a "
                        + "registered project directory."));
    }

    public Task add(
            Project project, String title, String description, TaskPriority priority, LocalDate dueDate) {
        if (project.status() == ProjectStatus.ARCHIVED) {
            throw new TaskException("Project '" + project.name() + "' is archived. Run 'devforge project unarchive "
                    + project.id() + "' to add tasks.");
        }
        String cleanTitle = requireTitle(title);
        String cleanDescription = cleanDescription(description);
        TaskPriority taskPriority = priority == null ? TaskPriority.MEDIUM : priority;
        Instant now = clock.instant();
        return tx.inTransaction("Could not create the task", c -> {
            Task task = tasks.insert(c, project.id(), cleanTitle, cleanDescription, taskPriority, dueDate, now);
            activity.record(c, project.id(), MODULE, "task.add", "task=" + task.id(), now);
            return task;
        });
    }

    public Task get(long id) {
        return tx.read("Could not read the task", c -> tasks.findById(c, id))
                .orElseThrow(() -> notFound(id));
    }

    public List<Task> list(Long projectId, TaskStatus status, TaskPriority priority, TaskSort sort) {
        TaskFilter filter = new TaskFilter(projectId, status, priority);
        return tx.read("Could not list tasks", c -> tasks.findAll(c, filter, sort));
    }

    public Task update(long id, TaskChanges changes) {
        if (changes.isEmpty()) {
            throw new TaskException("Nothing to update. Specify at least one change.");
        }
        Instant now = clock.instant();
        return tx.inTransaction("Could not update the task", c -> {
            Task current = tasks.findById(c, id).orElseThrow(() -> notFound(id));
            LocalDate due = changes.clearDueDate()
                    ? null
                    : (changes.dueDate() != null ? changes.dueDate() : current.dueDate());
            Task updated = current.withDetails(
                    changes.title() != null ? requireTitle(changes.title()) : current.title(),
                    changes.description() != null
                            ? cleanDescription(changes.description())
                            : current.description(),
                    changes.priority() != null ? changes.priority() : current.priority(),
                    due,
                    now);
            if (changes.status() != null) {
                updated = updated.withStatus(changes.status(), now);
            }
            if (!tasks.update(c, updated)) {
                throw notFound(id);
            }
            activity.record(c, current.projectId(), MODULE, "task.update", "task=" + id, now);
            return updated;
        });
    }

    public Task complete(long id) {
        Instant now = clock.instant();
        return tx.inTransaction("Could not complete the task", c -> {
            Task current = tasks.findById(c, id).orElseThrow(() -> notFound(id));
            if (current.status() == TaskStatus.DONE) {
                throw new TaskException("Task " + id + " is already completed.");
            }
            Task done = current.withStatus(TaskStatus.DONE, now);
            if (!tasks.update(c, done)) {
                throw notFound(id);
            }
            activity.record(c, current.projectId(), MODULE, "task.complete", "task=" + id, now);
            return done;
        });
    }

    /** Moves a completed task back to TODO. */
    public Task reopen(long id) {
        Instant now = clock.instant();
        return tx.inTransaction("Could not reopen the task", c -> {
            Task current = tasks.findById(c, id).orElseThrow(() -> notFound(id));
            if (current.status() != TaskStatus.DONE) {
                throw new TaskException("Task " + id + " is not completed, so it cannot be reopened.");
            }
            Task reopened = current.withStatus(TaskStatus.TODO, now);
            if (!tasks.update(c, reopened)) {
                throw notFound(id);
            }
            activity.record(c, current.projectId(), MODULE, "task.reopen", "task=" + id, now);
            return reopened;
        });
    }

    /** Deletes a task and returns what was deleted. */
    public Task delete(long id) {
        Instant now = clock.instant();
        return tx.inTransaction("Could not delete the task", c -> {
            Task current = tasks.findById(c, id).orElseThrow(() -> notFound(id));
            if (!tasks.delete(c, id)) {
                throw notFound(id);
            }
            activity.record(c, current.projectId(), MODULE, "task.delete", "task=" + id, now);
            return current;
        });
    }

    /** Progress statistics for one project, or for all projects when {@code projectId} is null. */
    public TaskStats stats(Long projectId) {
        List<Task> all = list(projectId, null, null, TaskSort.ID);
        LocalDate today = today();
        int todo = 0;
        int inProgress = 0;
        int blocked = 0;
        int done = 0;
        int overdue = 0;
        Map<TaskPriority, Integer> byPriority = new EnumMap<>(TaskPriority.class);
        for (TaskPriority p : TaskPriority.values()) {
            byPriority.put(p, 0);
        }
        for (Task t : all) {
            switch (t.status()) {
                case TODO -> todo++;
                case IN_PROGRESS -> inProgress++;
                case BLOCKED -> blocked++;
                case DONE -> done++;
            }
            if (t.isOverdue(today)) {
                overdue++;
            }
            byPriority.merge(t.priority(), 1, Integer::sum);
        }
        double percent = all.isEmpty() ? 0.0 : Math.round(done * 1000.0 / all.size()) / 10.0;
        return new TaskStats(all.size(), todo, inProgress, blocked, done, overdue, percent, byPriority);
    }

    private static String requireTitle(String title) {
        String clean = title == null ? "" : title.strip();
        if (clean.isEmpty()) {
            throw new TaskException("Task title must not be empty.");
        }
        if (clean.length() > MAX_TITLE_LENGTH) {
            throw new TaskException("Task title is too long (maximum " + MAX_TITLE_LENGTH + " characters).");
        }
        return clean;
    }

    private static String cleanDescription(String description) {
        String clean = description == null ? "" : description.strip();
        if (clean.length() > MAX_DESCRIPTION_LENGTH) {
            throw new TaskException(
                    "Task description is too long (maximum " + MAX_DESCRIPTION_LENGTH + " characters).");
        }
        return clean;
    }

    private static TaskException notFound(long id) {
        return new TaskException("Task " + id + " not found.");
    }
}

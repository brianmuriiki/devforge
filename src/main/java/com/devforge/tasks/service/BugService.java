package com.devforge.tasks.service;

import com.devforge.core.db.ConnectionProvider;
import com.devforge.core.db.Transactions;
import com.devforge.core.project.ActivityLogRepository;
import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectRegistry;
import com.devforge.core.project.ProjectStatus;
import com.devforge.tasks.model.Bug;
import com.devforge.tasks.model.BugSeverity;
import com.devforge.tasks.model.BugStatus;
import com.devforge.tasks.repo.BugFilter;
import com.devforge.tasks.repo.BugRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bug tracking rules: validation, transactions and activity logging. No CLI knowledge. */
public final class BugService {

    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_TEXT_LENGTH = 10_000;
    private static final String MODULE = "tasks";

    private final ProjectRegistry projects;
    private final Clock clock;
    private final Transactions tx;
    private final BugRepository bugs = new BugRepository();
    private final ActivityLogRepository activity = new ActivityLogRepository();

    public BugService(ConnectionProvider provider, ProjectRegistry projects, Clock clock) {
        this.tx = new Transactions(Objects.requireNonNull(provider, "provider"));
        this.projects = Objects.requireNonNull(projects, "projects");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The given project (id or name), or else the registered project containing the directory. */
    public Project resolveProject(String reference, Path workingDirectory) {
        if (reference != null) {
            return projects.resolve(reference);
        }
        return projects.findContaining(workingDirectory).orElseThrow(() -> new BugException(
                "No project specified. Use --project <id|name>, or run this command inside a "
                        + "registered project directory."));
    }

    public Bug add(
            Project project,
            String title,
            String description,
            BugSeverity severity,
            String reproductionSteps) {
        if (project.status() == ProjectStatus.ARCHIVED) {
            throw new BugException("Project '" + project.name() + "' is archived. Run 'devforge project "
                    + "unarchive " + project.id() + "' to add bugs.");
        }
        String cleanTitle = requireTitle(title);
        String cleanDescription = cleanText(description, "description");
        String cleanSteps = cleanText(reproductionSteps, "reproduction steps");
        BugSeverity bugSeverity = severity == null ? BugSeverity.MEDIUM : severity;
        Instant now = clock.instant();
        return tx.inTransaction("Could not record the bug", c -> {
            Bug bug = bugs.insert(c, project.id(), cleanTitle, cleanDescription, bugSeverity, cleanSteps, now);
            activity.record(c, project.id(), MODULE, "bug.add", "bug=" + bug.id(), now);
            return bug;
        });
    }

    public Bug get(long id) {
        return tx.read("Could not read the bug", c -> bugs.findById(c, id))
                .orElseThrow(() -> notFound(id));
    }

    public List<Bug> list(Long projectId, BugStatus status, BugSeverity severity) {
        BugFilter filter = new BugFilter(projectId, status, severity);
        return tx.read("Could not list bugs", c -> bugs.findAll(c, filter));
    }

    public Bug update(long id, BugChanges changes) {
        if (changes.isEmpty()) {
            throw new BugException("Nothing to update. Specify at least one change.");
        }
        Instant now = clock.instant();
        return tx.inTransaction("Could not update the bug", c -> {
            Bug current = bugs.findById(c, id).orElseThrow(() -> notFound(id));
            Bug updated = current.withDetails(
                    changes.title() != null ? requireTitle(changes.title()) : current.title(),
                    changes.description() != null
                            ? cleanText(changes.description(), "description")
                            : current.description(),
                    changes.severity() != null ? changes.severity() : current.severity(),
                    changes.reproductionSteps() != null
                            ? cleanText(changes.reproductionSteps(), "reproduction steps")
                            : current.reproductionSteps(),
                    now);
            if (changes.status() != null) {
                updated = updated.withStatus(changes.status(), now);
            }
            if (!bugs.update(c, updated)) {
                throw notFound(id);
            }
            activity.record(c, current.projectId(), MODULE, "bug.update", "bug=" + id, now);
            return updated;
        });
    }

    /** Number of bugs per status (all statuses present), for one project or for all when null. */
    public Map<BugStatus, Integer> statusCounts(Long projectId) {
        Map<BugStatus, Integer> counts = new EnumMap<>(BugStatus.class);
        for (BugStatus s : BugStatus.values()) {
            counts.put(s, 0);
        }
        for (Bug b : list(projectId, null, null)) {
            counts.merge(b.status(), 1, Integer::sum);
        }
        return counts;
    }

    private static String requireTitle(String title) {
        String clean = title == null ? "" : title.strip();
        if (clean.isEmpty()) {
            throw new BugException("Bug title must not be empty.");
        }
        if (clean.length() > MAX_TITLE_LENGTH) {
            throw new BugException("Bug title is too long (maximum " + MAX_TITLE_LENGTH + " characters).");
        }
        return clean;
    }

    private static String cleanText(String text, String label) {
        String clean = text == null ? "" : text.strip();
        if (clean.length() > MAX_TEXT_LENGTH) {
            throw new BugException("Bug " + label + " is too long (maximum " + MAX_TEXT_LENGTH + " characters).");
        }
        return clean;
    }

    private static BugException notFound(long id) {
        return new BugException("Bug " + id + " not found.");
    }
}

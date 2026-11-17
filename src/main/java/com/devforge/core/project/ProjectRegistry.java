package com.devforge.core.project;

import com.devforge.core.db.ConnectionProvider;
import com.devforge.core.db.DatabaseException;
import com.devforge.core.db.Transactions;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Registers and manages projects. Holds the business rules; the CLI only calls this class. */
public final class ProjectRegistry {

    static final int MAX_NAME_LENGTH = 100;

    private final ConnectionProvider provider;
    private final Clock clock;
    private final Transactions tx;
    private final ProjectRepository projects = new ProjectRepository();
    private final ActivityLogRepository activity = new ActivityLogRepository();

    public ProjectRegistry(ConnectionProvider provider, Clock clock) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tx = new Transactions(provider);
    }

    /**
     * Registers a directory. The directory is only inspected, never modified.
     *
     * @param requestedName optional name; defaults to the directory's name
     * @throws ProjectException if the directory is invalid, already registered, or the name is taken
     */
    public Project register(Path directory, String requestedName, String description) {
        Path root = ProjectPaths.canonicalDirectory(directory);
        String rootPath = root.toString();
        String name = resolveName(root, requestedName);
        String desc = description == null ? "" : description.strip();
        Instant now = clock.instant();

        try (Connection c = provider.open()) {
            c.setAutoCommit(false);
            try {
                // Insert first: the UNIQUE constraints are the atomic duplicate check.
                Project project = projects.insert(c, name, rootPath, desc, now);
                activity.record(c, project.id(), "core", "project.register", rootPath, now);
                c.commit();
                return project;
            } catch (SQLException e) {
                rollbackQuietly(c, e);
                throw translate(c, e, name, rootPath);
            } catch (RuntimeException e) {
                rollbackQuietly(c, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not register the project", e);
        }
    }

    public List<Project> list() {
        try (Connection c = provider.open()) {
            return projects.findAll(c);
        } catch (SQLException e) {
            throw new DatabaseException("Could not read the project list", e);
        }
    }

    public int count() {
        try (Connection c = provider.open()) {
            return projects.count(c);
        } catch (SQLException e) {
            throw new DatabaseException("Could not count projects", e);
        }
    }

    public Optional<Project> findById(long id) {
        try (Connection c = provider.open()) {
            return projects.findById(c, id);
        } catch (SQLException e) {
            throw new DatabaseException("Could not read the project", e);
        }
    }

    /**
     * Finds a project by id or name. A numeric reference is tried as an id first, then as a name.
     *
     * @throws ProjectException if nothing matches
     */
    public Project resolve(String reference) {
        String ref = reference == null ? "" : reference.strip();
        if (ref.isEmpty()) {
            throw new ProjectException("Project reference must not be empty.");
        }
        try (Connection c = provider.open()) {
            Long id = parseId(ref);
            if (id != null) {
                Optional<Project> byId = projects.findById(c, id);
                if (byId.isPresent()) {
                    return byId.get();
                }
            }
            return projects.findByName(c, ref).orElseThrow(() -> new ProjectException(
                    "No project found matching '" + ref
                            + "'. Run 'devforge project list' to see registered projects."));
        } catch (SQLException e) {
            throw new DatabaseException("Could not look up the project", e);
        }
    }

    /** The active project whose root contains {@code directory}; the deepest root wins. */
    public Optional<Project> findContaining(Path directory) {
        Path dir;
        try {
            dir = directory.toAbsolutePath().toRealPath();
        } catch (IOException e) {
            return Optional.empty();
        }
        Project best = null;
        int bestDepth = -1;
        for (Project p : list()) {
            if (p.status() != ProjectStatus.ACTIVE) {
                continue;
            }
            Path root = Path.of(p.rootPath());
            if (dir.startsWith(root) && root.getNameCount() > bestDepth) {
                best = p;
                bestDepth = root.getNameCount();
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Changes name and/or description; a null argument leaves that field unchanged.
     *
     * @throws ProjectException if nothing is requested, the project is missing, or the name is taken
     */
    public Project update(long id, String newName, String newDescription) {
        if (newName == null && newDescription == null) {
            throw new ProjectException("Nothing to update. Specify --name or --description.");
        }
        String cleanName = newName == null ? null : requireValidName(newName);
        String cleanDescription = newDescription == null ? null : newDescription.strip();
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return tx.inTransaction("Could not update the project", c -> {
            Project current = projects.findById(c, id).orElseThrow(() -> notFound(id));
            Project updated = new Project(
                    current.id(),
                    cleanName != null ? cleanName : current.name(),
                    current.rootPath(),
                    cleanDescription != null ? cleanDescription : current.description(),
                    current.status(),
                    current.createdAt(),
                    now);
            try {
                projects.update(c, updated);
            } catch (SQLException e) {
                if (String.valueOf(e.getMessage()).contains("projects.name")) {
                    throw new ProjectException("A project named '" + cleanName
                            + "' already exists. Choose a different name.");
                }
                throw e;
            }
            activity.record(c, id, "core", "project.update", null, now);
            return updated;
        });
    }

    public Project archive(long id) {
        return changeStatus(id, ProjectStatus.ARCHIVED, "project.archive");
    }

    public Project unarchive(long id) {
        return changeStatus(id, ProjectStatus.ACTIVE, "project.unarchive");
    }

    /**
     * Removes the project, its tasks and its bugs from DevForge's database. The project's
     * directory on disk is never touched. Activity history is kept.
     *
     * @return the project that was removed
     */
    public Project remove(long id) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return tx.inTransaction("Could not remove the project", c -> {
            Project current = projects.findById(c, id).orElseThrow(() -> notFound(id));
            if (!projects.delete(c, id)) {
                throw notFound(id);
            }
            activity.record(c, null, "core", "project.remove",
                    "id=" + id + " name=" + current.name(), now);
            return current;
        });
    }

    private Project changeStatus(long id, ProjectStatus target, String action) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return tx.inTransaction("Could not change the project status", c -> {
            Project current = projects.findById(c, id).orElseThrow(() -> notFound(id));
            if (current.status() == target) {
                throw new ProjectException("Project '" + current.name() + "' is already "
                        + target.name().toLowerCase(Locale.ROOT) + ".");
            }
            Project updated = new Project(current.id(), current.name(), current.rootPath(),
                    current.description(), target, current.createdAt(), now);
            if (!projects.update(c, updated)) {
                throw notFound(id);
            }
            activity.record(c, id, "core", action, null, now);
            return updated;
        });
    }

    private static ProjectException notFound(long id) {
        return new ProjectException("No project found with id " + id + ".");
    }

    private static String requireValidName(String requested) {
        String name = requested.strip();
        if (name.isEmpty()) {
            throw new ProjectException("Project name must not be empty.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ProjectException("Project name is too long (maximum " + MAX_NAME_LENGTH + " characters).");
        }
        return name;
    }

    private static Long parseId(String ref) {
        try {
            return Long.valueOf(ref);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String resolveName(Path root, String requestedName) {
        String name;
        if (requestedName != null) {
            name = requestedName.strip();
        } else {
            Path fileName = root.getFileName();
            name = fileName == null ? "" : fileName.toString().strip();
            if (name.isEmpty()) {
                name = "root"; // e.g. a filesystem root such as "/" or "C:\"
            }
        }
        if (name.isEmpty()) {
            throw new ProjectException("Project name must not be empty.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ProjectException("Project name is too long (maximum " + MAX_NAME_LENGTH + " characters).");
        }
        return name;
    }

    private RuntimeException translate(Connection c, SQLException e, String name, String rootPath) {
        String message = String.valueOf(e.getMessage());
        if (message.contains("projects.root_path")) {
            try {
                Optional<Project> existing = projects.findByRootPath(c, rootPath);
                if (existing.isPresent()) {
                    Project p = existing.get();
                    return new ProjectException("Project is already registered: '" + p.name()
                            + "' (id " + p.id() + ") at " + p.rootPath());
                }
            } catch (SQLException lookupFailure) {
                e.addSuppressed(lookupFailure);
            }
            return new ProjectException("Project is already registered: " + rootPath);
        }
        if (message.contains("projects.name")) {
            return new ProjectException(
                    "A project named '" + name + "' already exists. Choose a different name with --name.");
        }
        return new DatabaseException("Could not register the project", e);
    }

    private static void rollbackQuietly(Connection c, Exception original) {
        try {
            c.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}

package com.devforge.core;

import com.devforge.core.config.Configuration;
import com.devforge.core.config.ConfigurationManager;
import com.devforge.core.db.ConnectionProvider;
import com.devforge.core.db.DatabaseInfo;
import com.devforge.core.db.DatabaseManager;
import com.devforge.core.paths.AppPaths;
import com.devforge.core.project.ProjectRegistry;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;

/**
 * Shared services for one DevForge run. The database is created and migrated on first use,
 * so commands like {@code --help} never touch the disk.
 */
public final class AppContext {

    private final AppPaths paths;
    private final Clock clock;
    private final Prompter prompter;
    private final Path homeDir;
    private DatabaseManager databaseManager;
    private DatabaseInfo databaseInfo;
    private Configuration configuration;

    /** A context that cannot prompt: confirmations fail unless {@code --yes} is given. */
    public AppContext(AppPaths paths, Clock clock) {
        this(paths, clock, Prompter.unavailable());
    }

    public AppContext(AppPaths paths, Clock clock, Prompter prompter) {
        this(paths, clock, prompter, Path.of(System.getProperty("user.home")));
    }

    /** @param homeDir the user's home directory; DevVault only reads and restores files below it */
    public AppContext(AppPaths paths, Clock clock, Prompter prompter, Path homeDir) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.prompter = Objects.requireNonNull(prompter, "prompter");
        this.homeDir = Objects.requireNonNull(homeDir, "homeDir").toAbsolutePath().normalize();
    }

    public static AppContext system() {
        return new AppContext(AppPaths.system(), Clock.systemUTC(), Prompter.system());
    }

    public AppPaths paths() {
        return paths;
    }

    public Clock clock() {
        return clock;
    }

    public Prompter prompter() {
        return prompter;
    }

    public Path homeDir() {
        return homeDir;
    }

    /** Loads {@code config.json} on first use. */
    public synchronized Configuration config() {
        if (configuration == null) {
            configuration = ConfigurationManager.load(paths, System.getenv());
        }
        return configuration;
    }

    /** Initializes the database if needed and returns its state. */
    public synchronized DatabaseInfo databaseInfo() {
        ensureInitialized();
        return databaseInfo;
    }

    public synchronized ProjectRegistry projectRegistry() {
        ensureInitialized();
        return new ProjectRegistry(databaseManager.connectionProvider(), clock);
    }

    /** Connection source for modules that build their own services. */
    public synchronized ConnectionProvider connectionProvider() {
        ensureInitialized();
        return databaseManager.connectionProvider();
    }

    private void ensureInitialized() {
        if (databaseInfo == null) {
            DatabaseManager manager = new DatabaseManager(paths, clock);
            databaseInfo = manager.initialize();
            databaseManager = manager;
        }
    }
}

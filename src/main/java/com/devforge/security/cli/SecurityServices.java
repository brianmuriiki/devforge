package com.devforge.security.cli;

import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectRegistry;
import com.devforge.security.SecurityScanException;
import com.devforge.security.provider.JdkHttpTransport;
import com.devforge.security.provider.OsvAdvisoryProvider;
import com.devforge.security.provider.RegistryMetadataProvider;
import com.devforge.security.service.SecurityService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Wires DepGuard from the shared context. */
public final class SecurityServices {

    private SecurityServices() {}

    public static SecurityService create(AppContext context) {
        JdkHttpTransport http = new JdkHttpTransport(
                Duration.ofSeconds(context.config().httpTimeoutSeconds()), VersionProvider.appVersion());
        return new SecurityService(
                context.connectionProvider(), context.config(), context.clock(),
                new OsvAdvisoryProvider(http), new RegistryMetadataProvider(http));
    }

    /** The named project, else the project containing {@code directory}, else every registered project. */
    static List<Project> projects(AppContext context, String reference, Path directory) {
        ProjectRegistry registry = context.projectRegistry();
        if (reference != null) {
            return List.of(registry.resolve(reference));
        }
        var containing = registry.findContaining(directory);
        if (containing.isPresent()) {
            return List.of(containing.get());
        }
        List<Project> all = registry.list();
        if (all.isEmpty()) {
            throw new SecurityScanException("No projects are registered. Run 'devforge init .' first.");
        }
        return all;
    }

    /** Exactly one project: the named one, or the one containing {@code directory}. */
    static Project single(AppContext context, String reference, Path directory) {
        ProjectRegistry registry = context.projectRegistry();
        if (reference != null) {
            return registry.resolve(reference);
        }
        return registry.findContaining(directory).orElseThrow(() -> new SecurityScanException(
                "No registered project contains " + directory.toAbsolutePath()
                        + ". Register it with 'devforge init <dir>' or pass --project."));
    }
}

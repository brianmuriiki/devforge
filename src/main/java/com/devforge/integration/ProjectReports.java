package com.devforge.integration;

import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.environment.cli.VaultJson;
import com.devforge.environment.cli.VaultServices;
import com.devforge.environment.model.BackupRecord;
import com.devforge.environment.service.VaultService;
import com.devforge.search.cli.SearchJson;
import com.devforge.search.service.IndexService;
import com.devforge.security.cli.SecurityJson;
import com.devforge.security.cli.SecurityServices;
import com.devforge.security.model.SecurityReport;
import com.devforge.security.service.SecurityReports;
import com.devforge.tasks.cli.TaskStatsCommand;
import com.devforge.tasks.service.BugService;
import com.devforge.tasks.service.TaskService;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds and renders combined project reports. Lives above the modules and composes their services. */
final class ProjectReports {

    private ProjectReports() {}

    static ProjectReport build(AppContext context, Project project) {
        TaskService tasks = new TaskService(context.connectionProvider(), context.projectRegistry(), context.clock(),
                ZoneId.systemDefault());
        BugService bugs = new BugService(context.connectionProvider(), context.projectRegistry(), context.clock());
        IndexService index = new IndexService(context.connectionProvider(), context.projectRegistry(), context.config(),
                context.clock());
        VaultService vault = VaultServices.create(context);
        List<BackupRecord> backups = vault.backups();
        return new ProjectReport(
                project,
                tasks.stats(project.id()),
                bugs.statusCounts(project.id()),
                index.status(project),
                index.duplicates(project.id()),
                SecurityServices.create(context).report(project),
                backups.size(),
                backups.isEmpty() ? null : backups.get(backups.size() - 1));
    }

    /** 3 if the project has security findings, 4 if its security result is untrustworthy, else 0. */
    static int exitCode(ProjectReport r) {
        return r.security().scanned() ? SecurityReports.exitCode(r.security()) : 0;
    }

    static Map<String, Object> toMap(ProjectReport r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("project", com.devforge.cli.JsonOutput.project(r.project()));
        m.putAll(TaskStatsCommand.toMap(r.tasks(), r.bugs()));
        Map<String, Object> search = new LinkedHashMap<>(SearchJson.status(r.index()));
        search.putAll(SearchJson.duplicates(r.duplicates()));
        m.put("search", search);
        m.put("security", SecurityJson.report(r.security(), false));
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("backupCount", r.backupCount());
        env.put("latestBackup", r.latestBackup() == null ? null : VaultJson.backup(r.latestBackup()));
        m.put("environment", env);
        return m;
    }

    static void print(java.io.PrintWriter out, ProjectReport r) {
        String name = com.devforge.cli.TerminalText.inline(r.project().name());
        out.println("== " + name + " (" + com.devforge.cli.TerminalText.inline(r.project().rootPath()) + ") ==");
        out.println("Tasks:      " + r.tasks().total() + " total, " + r.tasks().done() + " done ("
                + r.tasks().completionPercent() + "%), " + r.tasks().inProgress() + " in progress, "
                + r.tasks().blocked() + " blocked, " + r.tasks().overdue() + " overdue");
        out.println("Bugs:       " + r.bugs());
        out.println("Index:      " + r.index().files() + " files (" + r.index().contentIndexed() + " searchable), "
                + (r.index().lastIndexedAt() == null ? "never indexed" : "indexed " + r.index().lastIndexedAt()));
        out.println("Duplicates: " + r.duplicates().groups().size() + " group(s), " + r.duplicates().wastedBytes()
                + " bytes reclaimable");
        SecurityReport s = r.security();
        if (!s.scanned()) {
            out.println("Security:   not scanned (no manifests found, or run 'devforge security scan')");
        } else {
            out.println("Security:   " + s.overallStatus() + " - " + s.vulnerabilityCount() + " finding(s) in "
                    + s.vulnerableDependencies() + " of " + s.totalDependencies() + " dependencies; " + s.outdated()
                    + " outdated; " + s.licenseWarnings() + " license(s) to review");
            if (s.overallStatus() == com.devforge.security.model.ScanStatus.INCOMPLETE
                    || s.overallStatus() == com.devforge.security.model.ScanStatus.UNAVAILABLE) {
                out.println("            WARNING: the security result is " + s.overallStatus() + " and is NOT a clean result.");
            }
        }
        out.println("Backups:    " + r.backupCount() + " registered"
                + (r.latestBackup() == null ? "" : ", latest " + r.latestBackup().createdAt()
                        + (r.latestBackup().verified() ? " (verified)" : " (NOT verified)")));
    }
}

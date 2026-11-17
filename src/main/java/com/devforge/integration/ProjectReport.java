package com.devforge.integration;

import com.devforge.core.project.Project;
import com.devforge.environment.model.BackupRecord;
import com.devforge.search.model.DuplicateReport;
import com.devforge.search.model.IndexStatus;
import com.devforge.security.model.SecurityReport;
import com.devforge.tasks.model.BugStatus;
import com.devforge.tasks.model.TaskStats;
import java.util.Map;

/**
 * Everything DevForge knows about one project, gathered from all four modules.
 *
 * @param backupCount  number of registered backups (backups are not tied to a single project)
 * @param latestBackup the most recent backup, or null
 */
public record ProjectReport(
        Project project,
        TaskStats tasks,
        Map<BugStatus, Integer> bugs,
        IndexStatus index,
        DuplicateReport duplicates,
        SecurityReport security,
        int backupCount,
        BackupRecord latestBackup) {}

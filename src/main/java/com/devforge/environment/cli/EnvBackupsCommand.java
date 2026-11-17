package com.devforge.environment.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TextTable;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.environment.model.BackupRecord;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
        name = "backups",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "List registered backups.")
public class EnvBackupsCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    private final AppContext context;

    public EnvBackupsCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        List<BackupRecord> backups = VaultServices.create(context).backups();
        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (BackupRecord b : backups) {
                items.add(VaultJson.backup(b));
            }
            Map<String, Object> doc = JsonOutput.document();
            doc.put("count", backups.size());
            doc.put("backups", items);
            out.println(JsonOutput.toJson(doc));
        } else if (backups.isEmpty()) {
            out.println("No backups yet. Run 'devforge env backup'.");
        } else {
            List<List<String>> rows = new ArrayList<>();
            for (BackupRecord b : backups) {
                rows.add(List.of(String.valueOf(b.id()), b.profileName() == null ? "(deleted)" : b.profileName(),
                        b.createdAt().toString(), String.valueOf(b.fileCount()), b.sizeBytes() + " B",
                        b.verified() ? "yes" : "NO", b.archivePath()));
            }
            out.print(TextTable.render(List.of("ID", "PROFILE", "CREATED", "FILES", "SIZE", "VERIFIED", "ARCHIVE"), rows));
        }
        out.flush();
    }
}

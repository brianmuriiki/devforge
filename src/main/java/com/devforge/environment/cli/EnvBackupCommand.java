package com.devforge.environment.cli;

import com.devforge.cli.ConfirmOptions;
import com.devforge.cli.Confirmation;
import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.environment.engine.FileSelector.Selection;
import com.devforge.environment.model.VaultProfile;
import com.devforge.environment.service.VaultService;
import com.devforge.environment.service.VaultService.BackupOutcome;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "backup",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = {
            "Create a verified, compressed backup of a profile. Secrets (private keys, tokens, .env files, "
                    + "credential stores) are excluded unless --include-sensitive is given.",
            "Exit code 4 = completed with warnings (for example, missing paths)."})
public class EnvBackupCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Mixin ConfirmOptions confirm;

    @Option(names = {"-p", "--profile"}, paramLabel = "<name>", description = "Profile (default: the only profile).")
    String profile;

    @Option(names = "--include-sensitive",
            description = "Also back up files that look like secrets. The archive is NOT encrypted.")
    boolean includeSensitive;

    @Option(names = "--dry-run", description = "Show what would be backed up without writing anything.")
    boolean dryRun;

    private final AppContext context;

    public EnvBackupCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        VaultService service = VaultServices.create(context);
        VaultProfile p = service.resolveProfile(profile);
        Selection sel = service.select(p, includeSensitive);
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        if (includeSensitive && !sel.sensitiveIncluded().isEmpty() && !dryRun) {
            Confirmation.require(context, confirm,
                    "WARNING: this backup will include " + sel.sensitiveIncluded().size()
                            + " file(s) that look like secrets (keys, tokens, credentials). The archive is NOT encrypted: "
                            + "anyone who can read it can use them. Continue?");
        }
        int warnings = sel.missing().size() + (sel.symlinksSkipped() > 0 ? 1 : 0) + (sel.unreadable() > 0 ? 1 : 0);

        if (dryRun) {
            if (output.json) {
                Map<String, Object> doc = JsonOutput.document();
                doc.put("dryRun", true);
                doc.put("profile", p.name());
                doc.put("fileCount", sel.files().size());
                doc.put("totalBytes", sel.totalBytes());
                doc.put("sensitiveExcluded", sel.sensitiveExcluded());
                doc.put("missing", sel.missing());
                out.println(JsonOutput.toJson(doc));
            } else {
                out.println("Dry run for profile '" + TerminalText.inline(p.name()) + "': " + sel.files().size()
                        + " file(s), " + sel.totalBytes() + " bytes would be backed up. Nothing was written.");
                notes(out, sel);
            }
            out.flush();
            return warnings > 0 ? 4 : 0;
        }

        BackupOutcome outcome = service.backup(p, includeSensitive, sel);
        warnings += outcome.skippedUnreadable().size();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("backup", VaultJson.backup(outcome.record()));
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("sensitiveExcluded", sel.sensitiveExcluded().size());
            details.put("missing", sel.missing());
            details.put("symlinksSkipped", sel.symlinksSkipped());
            details.put("skippedUnreadable", outcome.skippedUnreadable());
            doc.put("details", details);
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Backup " + outcome.record().id() + " created and verified: "
                    + TerminalText.inline(outcome.record().archivePath()));
            out.println("  " + outcome.record().fileCount() + " file(s), " + outcome.record().sizeBytes()
                    + " bytes, SHA-256 " + outcome.record().checksum());
            notes(out, sel);
            for (String s : outcome.skippedUnreadable()) {
                err.println("Warning: could not read " + TerminalText.inline(s) + "; it was skipped.");
            }
        }
        out.flush();
        err.flush();
        return warnings > 0 ? 4 : 0;
    }

    private static void notes(PrintWriter out, Selection sel) {
        if (!sel.sensitiveExcluded().isEmpty()) {
            out.println("  " + sel.sensitiveExcluded().size() + " sensitive file(s) excluded (private keys, tokens, .env, "
                    + "credentials). Use --include-sensitive to override.");
            sel.sensitiveExcluded().stream().limit(10).forEach(s -> out.println("    - " + TerminalText.inline(s)));
        }
        for (String m : sel.missing()) {
            out.println("  Warning: path not found: " + TerminalText.inline(m));
        }
        if (sel.symlinksSkipped() > 0) {
            out.println("  Warning: " + sel.symlinksSkipped() + " symbolic link(s) were not followed or archived.");
        }
        if (sel.unreadable() > 0) {
            out.println("  Warning: " + sel.unreadable() + " path(s) could not be read.");
        }
    }
}

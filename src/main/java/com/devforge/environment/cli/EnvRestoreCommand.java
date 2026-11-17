package com.devforge.environment.cli;

import com.devforge.cli.ConfirmOptions;
import com.devforge.cli.Confirmation;
import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.environment.engine.RestoreEngine;
import com.devforge.environment.engine.RestoreEngine.Action;
import com.devforge.environment.engine.RestoreEngine.Plan;
import com.devforge.environment.engine.RestoreEngine.PlanEntry;
import com.devforge.environment.engine.RestoreEngine.Result;
import com.devforge.environment.service.VaultService;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "restore",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = {
            "Restore a backup into your home directory. The archive is verified first; existing files are never "
                    + "overwritten without confirmation, and replaced files are saved under .devforge-recovery.",
            "Exit code 4 = completed with warnings (skipped conflicts or blocked paths)."})
public class EnvRestoreCommand implements Callable<Integer> {

    private static final int MAX_LISTED = 40;

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Mixin ConfirmOptions confirm;

    @Parameters(index = "0", arity = "0..1", paramLabel = "<id>", description = "Backup id (default: the latest backup).")
    Long id;

    @Option(names = "--archive", paramLabel = "<file>", description = "Restore from an archive file not registered here (for example, copied from another computer).")
    Path archive;

    @Option(names = "--checksum", paramLabel = "<sha256>", description = "Expected SHA-256 of --archive (default: the .sha256 file beside it).")
    String checksum;

    @Option(names = "--target-home", paramLabel = "<dir>", description = "Restore below this directory instead of your home directory.")
    Path targetHome;

    @Option(names = "--dry-run", description = "Show what would happen without writing anything.")
    boolean dryRun;

    @Option(names = "--skip-conflicts", description = "Restore only files that do not exist yet; leave differing files alone.")
    boolean skipConflicts;

    private final AppContext context;

    public EnvRestoreCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        VaultService service = VaultServices.create(context);
        Path home = targetHome != null ? targetHome : service.home();
        Plan plan = service.prepareRestore(id, archive, checksum, home);
        PrintWriter out = spec.commandLine().getOut();

        long create = plan.count(Action.CREATE);
        long identical = plan.count(Action.IDENTICAL);
        long conflicts = plan.count(Action.CONFLICT);
        long blocked = plan.count(Action.BLOCKED);

        if (dryRun) {
            if (output.json) {
                out.println(JsonOutput.toJson(planDocument(plan, create, identical, conflicts, blocked)));
            } else {
                out.println("Dry run: archive verified. Restoring into " + TerminalText.inline(plan.targetHome().toString()));
                out.println("  " + create + " to create, " + identical + " identical (skipped), " + conflicts
                        + " conflict(s), " + blocked + " blocked. Nothing was written.");
                list(out, plan);
            }
            out.flush();
            return conflicts + blocked > 0 ? 4 : 0;
        }

        boolean overwrite = false;
        if (conflicts > 0 && !skipConflicts) {
            list(out, plan);
            Confirmation.require(context, confirm,
                    conflicts + " existing file(s) differ from the backup and will be overwritten. Originals are saved under "
                            + plan.targetHome().resolve(RestoreEngine.RECOVERY_DIR) + ". Continue?");
            overwrite = true;
        }
        Result result = service.restore(plan, overwrite);

        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("restored", result.restored());
            r.put("skippedIdentical", result.skippedIdentical());
            r.put("conflictsSkipped", result.conflictsSkipped());
            r.put("recovered", result.recovered());
            r.put("recoveryDirectory", result.recoveryDir() == null ? null : result.recoveryDir().toString());
            r.put("failures", result.failures());
            doc.put("restore", r);
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Restored " + result.restored() + " file(s) (" + result.skippedIdentical() + " already identical)."
                    + (result.conflictsSkipped() > 0 ? " " + result.conflictsSkipped() + " differing file(s) left untouched." : ""));
            if (result.recoveryDir() != null) {
                out.println("Replaced files were saved to " + TerminalText.inline(result.recoveryDir().toString()));
            }
            result.failures().forEach(f -> spec.commandLine().getErr().println("Warning: " + TerminalText.inline(f)));
        }
        out.flush();
        spec.commandLine().getErr().flush();
        return result.failures().isEmpty() && result.conflictsSkipped() == 0 ? 0 : 4;
    }

    private static void list(PrintWriter out, Plan plan) {
        int shown = 0;
        for (PlanEntry e : plan.entries()) {
            if (e.action() == Action.IDENTICAL) {
                continue;
            }
            if (shown++ >= MAX_LISTED) {
                out.println("  ... and more");
                break;
            }
            out.println("  [" + e.action() + "] " + TerminalText.inline(e.entry().path())
                    + (e.reason().isEmpty() ? "" : " (" + e.reason() + ")"));
        }
    }

    private static Map<String, Object> planDocument(Plan plan, long create, long identical, long conflicts, long blocked) {
        Map<String, Object> doc = JsonOutput.document();
        doc.put("dryRun", true);
        doc.put("targetHome", plan.targetHome().toString());
        doc.put("create", create);
        doc.put("identical", identical);
        doc.put("conflicts", conflicts);
        doc.put("blocked", blocked);
        List<Map<String, Object>> entries = new ArrayList<>();
        for (PlanEntry e : plan.entries()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("path", e.entry().path());
            m.put("action", e.action().name());
            m.put("reason", e.reason());
            entries.add(m);
        }
        doc.put("entries", entries);
        return doc;
    }
}

package com.devforge.environment.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.environment.VaultException;
import com.devforge.environment.engine.ArchiveVerifier;
import com.devforge.environment.model.BackupRecord;
import com.devforge.environment.service.VaultService;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "verify",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Verify a backup's checksum and every file against its manifest, without extracting.")
public class EnvVerifyCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", arity = "0..1", paramLabel = "<id>", description = "Backup id (default: the latest backup).")
    Long id;

    @Option(names = "--archive", paramLabel = "<file>", description = "Verify an archive file that is not registered here.")
    Path archive;

    @Option(names = "--checksum", paramLabel = "<sha256>", description = "Expected SHA-256 (default: the .sha256 file beside the archive).")
    String checksum;

    private final AppContext context;

    public EnvVerifyCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        VaultService service = VaultServices.create(context);
        String label;
        ArchiveVerifier.Report report;
        if (archive != null) {
            label = archive.toString();
            report = service.verifyArchive(archive, checksum);
        } else {
            BackupRecord record = service.getBackup(id);
            label = "backup " + record.id();
            report = service.verify(record);
        }
        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", report.ok());
            result.put("filesChecked", report.filesChecked());
            result.put("problems", report.problems());
            doc.put("verification", result);
            out.println(JsonOutput.toJson(doc));
            out.flush();
        }
        if (!report.ok()) {
            report.problems().stream().limit(20).forEach(p -> spec.commandLine().getErr().println("  - " + TerminalText.inline(p)));
            throw new VaultException("Backup checksum verification failed for " + TerminalText.inline(label) + ".");
        }
        if (!output.json) {
            out.println("OK: " + TerminalText.inline(label) + " verified (" + report.filesChecked() + " file(s) match the manifest).");
        }
        out.flush();
    }
}

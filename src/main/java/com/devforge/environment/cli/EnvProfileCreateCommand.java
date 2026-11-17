package com.devforge.environment.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.environment.model.VaultProfile;
import com.devforge.environment.service.VaultService;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "create",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = {
            "Create a backup profile.",
            "Paths must be inside your home directory (use ~ for it). Without --include, common Git, shell "
                    + "and editor files are used."})
public class EnvProfileCreateCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", paramLabel = "<name>", description = "Profile name.")
    String name;

    @Option(names = "--include", paramLabel = "<path>", description = "File or directory to back up (repeatable).")
    List<String> includes = new ArrayList<>();

    @Option(names = "--exclude", paramLabel = "<glob>", description = "Glob pattern to leave out (repeatable).")
    List<String> excludes = new ArrayList<>();

    @Option(names = {"-d", "--description"}, defaultValue = "", description = "Description.")
    String description;

    @Option(names = "--destination", paramLabel = "<dir>", description = "Directory for this profile's backups.")
    String destination;

    private final AppContext context;

    public EnvProfileCreateCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        VaultService service = VaultServices.create(context);
        VaultProfile p = service.createProfile(name, description, includes, excludes, destination);
        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("profile", VaultJson.profile(p));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Created profile '" + TerminalText.inline(p.name()) + "' with " + p.includes().size()
                    + " include path(s)" + (includes.isEmpty() ? " (defaults)" : "") + " and " + p.excludes().size()
                    + " exclude pattern(s).");
        }
        out.flush();
    }
}

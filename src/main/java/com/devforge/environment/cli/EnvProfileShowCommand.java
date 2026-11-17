package com.devforge.environment.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.environment.model.VaultProfile;
import java.io.PrintWriter;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "show",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Show a backup profile.")
public class EnvProfileShowCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", paramLabel = "<name>", description = "Profile name.")
    String name;

    private final AppContext context;

    public EnvProfileShowCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        VaultProfile p = VaultServices.create(context).getProfile(name);
        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("profile", VaultJson.profile(p));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Profile " + TerminalText.inline(p.name()));
            if (!p.description().isEmpty()) {
                out.println("Description: " + TerminalText.inline(p.description()));
            }
            out.println("Destination: " + (p.destination() == null ? "(default backup directory)" : TerminalText.inline(p.destination())));
            out.println("Includes:");
            p.includes().forEach(i -> out.println("  " + TerminalText.inline(i)));
            out.println("Excludes:" + (p.excludes().isEmpty() ? " (none)" : ""));
            p.excludes().forEach(e -> out.println("  " + TerminalText.inline(e)));
        }
        out.flush();
    }
}

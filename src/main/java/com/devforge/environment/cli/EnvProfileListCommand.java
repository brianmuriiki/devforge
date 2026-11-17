package com.devforge.environment.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TextTable;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.environment.model.VaultProfile;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
        name = "list",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "List backup profiles.")
public class EnvProfileListCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    private final AppContext context;

    public EnvProfileListCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        List<VaultProfile> profiles = VaultServices.create(context).listProfiles();
        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (VaultProfile p : profiles) {
                items.add(VaultJson.profile(p));
            }
            Map<String, Object> doc = JsonOutput.document();
            doc.put("count", profiles.size());
            doc.put("profiles", items);
            out.println(JsonOutput.toJson(doc));
        } else if (profiles.isEmpty()) {
            out.println("No profiles. Create one with 'devforge env profile create <name>'.");
        } else {
            List<List<String>> rows = new ArrayList<>();
            for (VaultProfile p : profiles) {
                rows.add(List.of(p.name(), String.valueOf(p.includes().size()), String.valueOf(p.excludes().size()),
                        p.description()));
            }
            out.print(TextTable.render(List.of("NAME", "INCLUDES", "EXCLUDES", "DESCRIPTION"), rows));
        }
        out.flush();
    }
}

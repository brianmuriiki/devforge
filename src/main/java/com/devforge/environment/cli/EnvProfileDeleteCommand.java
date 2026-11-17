package com.devforge.environment.cli;

import com.devforge.cli.ConfirmOptions;
import com.devforge.cli.Confirmation;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.environment.service.VaultService;
import java.io.PrintWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "delete",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Delete a backup profile. Existing backup archives are kept.")
public class EnvProfileDeleteCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Mixin ConfirmOptions confirm;

    @Parameters(index = "0", paramLabel = "<name>", description = "Profile name.")
    String name;

    private final AppContext context;

    public EnvProfileDeleteCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        VaultService service = VaultServices.create(context);
        String profile = service.getProfile(name).name();
        Confirmation.require(context, confirm,
                "Delete profile '" + TerminalText.inline(profile) + "'? Existing backups and archives are not deleted.");
        service.deleteProfile(profile);
        PrintWriter out = spec.commandLine().getOut();
        out.println("Deleted profile '" + TerminalText.inline(profile) + "'.");
        out.flush();
    }
}

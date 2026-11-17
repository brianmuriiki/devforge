package com.devforge.cli;

import com.devforge.cli.commands.InitCommand;
import com.devforge.cli.commands.ProjectArchiveCommand;
import com.devforge.cli.commands.ProjectCommand;
import com.devforge.cli.commands.ProjectListCommand;
import com.devforge.cli.commands.ProjectRemoveCommand;
import com.devforge.cli.commands.ProjectShowCommand;
import com.devforge.cli.commands.ProjectUnarchiveCommand;
import com.devforge.cli.commands.ProjectUpdateCommand;
import com.devforge.cli.commands.StatusCommand;
import com.devforge.core.AppContext;
import com.devforge.core.DevForgeException;
import com.devforge.environment.cli.EnvBackupCommand;
import com.devforge.environment.cli.EnvBackupsCommand;
import com.devforge.environment.cli.EnvCommand;
import com.devforge.environment.cli.EnvProfileCommand;
import com.devforge.environment.cli.EnvProfileCreateCommand;
import com.devforge.environment.cli.EnvProfileDeleteCommand;
import com.devforge.environment.cli.EnvProfileListCommand;
import com.devforge.environment.cli.EnvProfileShowCommand;
import com.devforge.environment.cli.EnvRestoreCommand;
import com.devforge.environment.cli.EnvVerifyCommand;
import com.devforge.integration.AnalyzeCommand;
import com.devforge.integration.ReportCommand;
import com.devforge.search.cli.SearchCommand;
import com.devforge.search.cli.SearchDuplicatesCommand;
import com.devforge.search.cli.SearchIndexCommand;
import com.devforge.search.cli.SearchRebuildCommand;
import com.devforge.search.cli.SearchStatusCommand;
import com.devforge.search.cli.SearchWatchCommand;
import com.devforge.security.cli.SecurityAuditCommand;
import com.devforge.security.cli.SecurityCommand;
import com.devforge.security.cli.SecurityLicensesCommand;
import com.devforge.security.cli.SecurityOutdatedCommand;
import com.devforge.security.cli.SecurityReportCommand;
import com.devforge.security.cli.SecurityScanCommand;
import com.devforge.tasks.cli.BugAddCommand;
import com.devforge.tasks.cli.BugCommand;
import com.devforge.tasks.cli.BugListCommand;
import com.devforge.tasks.cli.BugShowCommand;
import com.devforge.tasks.cli.BugUpdateCommand;
import com.devforge.tasks.cli.TaskAddCommand;
import com.devforge.tasks.cli.TaskCommand;
import com.devforge.tasks.cli.TaskCompleteCommand;
import com.devforge.tasks.cli.TaskDeleteCommand;
import com.devforge.tasks.cli.TaskListCommand;
import com.devforge.tasks.cli.TaskReopenCommand;
import com.devforge.tasks.cli.TaskShowCommand;
import com.devforge.tasks.cli.TaskStatsCommand;
import com.devforge.tasks.cli.TaskUpdateCommand;
import java.io.PrintWriter;
import picocli.CommandLine;
import picocli.CommandLine.ParseResult;

/** Assembles the command tree and installs the central error handler. */
public final class CommandFactory {

    private CommandFactory() {}

    public static CommandLine create(AppContext context) {
        CommandLine root = new CommandLine(new DevForgeCommand());

        CommandLine project = new CommandLine(new ProjectCommand());
        project.addSubcommand(new ProjectListCommand(context));
        project.addSubcommand(new ProjectShowCommand(context));
        project.addSubcommand(new ProjectUpdateCommand(context));
        project.addSubcommand(new ProjectArchiveCommand(context));
        project.addSubcommand(new ProjectUnarchiveCommand(context));
        project.addSubcommand(new ProjectRemoveCommand(context));

        CommandLine task = new CommandLine(new TaskCommand());
        task.addSubcommand(new TaskAddCommand(context));
        task.addSubcommand(new TaskListCommand(context));
        task.addSubcommand(new TaskShowCommand(context));
        task.addSubcommand(new TaskUpdateCommand(context));
        task.addSubcommand(new TaskCompleteCommand(context));
        task.addSubcommand(new TaskReopenCommand(context));
        task.addSubcommand(new TaskDeleteCommand(context));
        task.addSubcommand(new TaskStatsCommand(context));

        CommandLine bug = new CommandLine(new BugCommand());
        bug.addSubcommand(new BugAddCommand(context));
        bug.addSubcommand(new BugListCommand(context));
        bug.addSubcommand(new BugShowCommand(context));
        bug.addSubcommand(new BugUpdateCommand(context));

        CommandLine search = new CommandLine(new SearchCommand(context));
        search.addSubcommand(new SearchIndexCommand(context));
        search.addSubcommand(new SearchRebuildCommand(context));
        search.addSubcommand(new SearchStatusCommand(context));
        search.addSubcommand(new SearchDuplicatesCommand(context));
        search.addSubcommand(new SearchWatchCommand(context));

        CommandLine security = new CommandLine(new SecurityCommand());
        security.addSubcommand(new SecurityScanCommand(context));
        security.addSubcommand(new SecurityAuditCommand(context));
        security.addSubcommand(new SecurityOutdatedCommand(context));
        security.addSubcommand(new SecurityLicensesCommand(context));
        security.addSubcommand(new SecurityReportCommand(context));

        CommandLine envProfile = new CommandLine(new EnvProfileCommand());
        envProfile.addSubcommand(new EnvProfileListCommand(context));
        envProfile.addSubcommand(new EnvProfileCreateCommand(context));
        envProfile.addSubcommand(new EnvProfileShowCommand(context));
        envProfile.addSubcommand(new EnvProfileDeleteCommand(context));

        CommandLine env = new CommandLine(new EnvCommand());
        env.addSubcommand(envProfile);
        env.addSubcommand(new EnvBackupCommand(context));
        env.addSubcommand(new EnvBackupsCommand(context));
        env.addSubcommand(new EnvVerifyCommand(context));
        env.addSubcommand(new EnvRestoreCommand(context));

        root.addSubcommand(new InitCommand(context));
        root.addSubcommand(new StatusCommand(context));
        root.addSubcommand(project);
        root.addSubcommand(task);
        root.addSubcommand(bug);
        root.addSubcommand(search);
        root.addSubcommand(security);
        root.addSubcommand(env);
        root.addSubcommand(new ReportCommand(context));
        root.addSubcommand(new AnalyzeCommand(context));

        // These settings apply to the commands registered so far, so they must come last.
        root.setCaseInsensitiveEnumValuesAllowed(true);
        root.setExecutionExceptionHandler(CommandFactory::handle);
        return root;
    }

    /** Expected failures print one line to stderr; anything else is reported as unexpected. */
    static int handle(Exception ex, CommandLine commandLine, ParseResult parseResult) {
        PrintWriter err = commandLine.getErr();
        boolean verbose = isVerbose(parseResult);
        if (ex instanceof DevForgeException) {
            err.println("Error: " + ex.getMessage());
        } else if (verbose) {
            err.println("Error: Unexpected failure: " + ex);
        } else {
            err.println("Error: Unexpected failure. Run again with --verbose for technical details.");
        }
        if (verbose) {
            ex.printStackTrace(err);
        }
        err.flush();
        return ExitCodes.FAILURE;
    }

    private static boolean isVerbose(ParseResult parseResult) {
        ParseResult leaf = parseResult;
        while (leaf.hasSubcommand()) {
            leaf = leaf.subcommand();
        }
        return leaf.hasMatchedOption("--verbose");
    }
}

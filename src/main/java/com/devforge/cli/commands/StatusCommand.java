package com.devforge.cli.commands;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.db.DatabaseInfo;
import java.io.PrintWriter;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
        name = "status",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Show DevForge version, database location and project count.")
public class StatusCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    private final AppContext context;

    public StatusCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        DatabaseInfo db = context.databaseInfo();
        int projectCount = context.projectRegistry().count();
        String version = VersionProvider.appVersion();
        PrintWriter out = spec.commandLine().getOut();

        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("version", version);
            doc.put("databasePath", db.path().toString());
            doc.put("databaseSchemaVersion", db.schemaVersion());
            doc.put("sqliteVersion", db.sqliteVersion());
            doc.put("journalMode", db.journalMode());
            doc.put("foreignKeysEnabled", db.foreignKeysEnabled());
            doc.put("projectCount", projectCount);
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("DevForge " + version);
            out.println("Database:       " + db.path());
            out.println("Schema version: " + db.schemaVersion());
            out.println("SQLite:         " + db.sqliteVersion() + " (journal: " + db.journalMode()
                    + ", foreign keys: " + (db.foreignKeysEnabled() ? "on" : "off") + ")");
            out.println("Projects:       " + projectCount);
        }
        out.flush();
    }
}

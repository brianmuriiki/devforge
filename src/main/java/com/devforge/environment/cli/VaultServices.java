package com.devforge.environment.cli;

import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.environment.service.VaultService;

/** Wires DevVault from the shared context. */
public final class VaultServices {

    private VaultServices() {}

    public static VaultService create(AppContext context) {
        return new VaultService(
                context.connectionProvider(), context.clock(), context.homeDir(),
                context.paths().dataDir().resolve("backups"), VersionProvider.appVersion());
    }
}

package com.devforge.tasks.cli;

import com.devforge.core.AppContext;
import com.devforge.tasks.service.BugService;

/** Builds the bug service from the shared context. */
final class BugServices {

    private BugServices() {}

    static BugService create(AppContext context) {
        return new BugService(context.connectionProvider(), context.projectRegistry(), context.clock());
    }
}

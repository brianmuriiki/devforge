package com.devforge.tasks.cli;

import com.devforge.core.AppContext;
import com.devforge.tasks.service.TaskService;
import java.time.ZoneId;

/** Builds the TaskForge service from the shared context. Core never knows about this module. */
final class TaskServices {

    private TaskServices() {}

    static TaskService create(AppContext context) {
        return new TaskService(
                context.connectionProvider(),
                context.projectRegistry(),
                context.clock(),
                ZoneId.systemDefault());
    }
}

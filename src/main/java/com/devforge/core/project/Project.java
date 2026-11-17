package com.devforge.core.project;

import java.time.Instant;

/** A registered project. {@code rootPath} is always a canonical absolute path. */
public record Project(
        long id,
        String name,
        String rootPath,
        String description,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt) {}

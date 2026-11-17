package com.devforge.environment.model;

import java.time.Instant;
import java.util.List;

/** A named backup profile. {@code destination} is null for the default backup directory. */
public record VaultProfile(
        long id, String name, String description, String destination, List<String> includes, List<String> excludes,
        Instant createdAt, Instant updatedAt) {}

package com.devforge.core.db;

/**
 * One schema change.
 *
 * @param version     1-based, contiguous, never reused
 * @param description short label stored in {@code schema_version}
 * @param resource    classpath location of the SQL script
 */
public record Migration(int version, String description, String resource) {}

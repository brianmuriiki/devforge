package com.devforge.security.model;

/** Registry information about a package. Either field may be null when unknown. */
public record PackageMetadata(String latestVersion, String license) {}

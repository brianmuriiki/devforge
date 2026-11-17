package com.devforge.security.model;

/**
 * A declared dependency.
 *
 * @param declaredVersion the version text as written in the manifest (may be a range, or empty)
 * @param resolvedVersion the exact version if it is known for certain (pinned or locked), else null
 */
public record Dependency(
        String name, String declaredVersion, String resolvedVersion, Ecosystem ecosystem, boolean direct, String scope) {}

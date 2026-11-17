package com.devforge.security.model;

/** One stored dependency of a scan, with registry metadata when available. */
public record DependencyRecord(
        long id, long scanId, String name, String declaredVersion, String resolvedVersion, boolean direct,
        String scope, CheckStatus checkStatus, String latestVersion, String license, boolean metadataOk) {}

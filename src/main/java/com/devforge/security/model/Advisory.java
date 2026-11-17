package com.devforge.security.model;

import java.util.List;

/** A normalized vulnerability advisory, independent of the provider it came from. */
public record Advisory(
        String id,
        String summary,
        Severity severity,
        List<String> fixedVersions,
        String affectedRange,
        List<String> references,
        List<String> aliases,
        String source) {}

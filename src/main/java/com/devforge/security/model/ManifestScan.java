package com.devforge.security.model;

import java.util.List;

public record ManifestScan(ScanRecord scan, List<DependencyRecord> dependencies, List<VulnerabilityRecord> vulnerabilities) {}

package com.devforge.security.service;

import com.devforge.core.config.Configuration;
import com.devforge.core.db.ConnectionProvider;
import com.devforge.core.db.Transactions;
import com.devforge.core.project.ActivityLogRepository;
import com.devforge.core.project.Project;
import com.devforge.security.SecurityScanException;
import com.devforge.security.model.Advisory;
import com.devforge.security.model.CheckStatus;
import com.devforge.security.model.Dependency;
import com.devforge.security.model.DependencyRecord;
import com.devforge.security.model.ManifestScan;
import com.devforge.security.model.PackageMetadata;
import com.devforge.security.model.ScanRecord;
import com.devforge.security.model.ScanStatus;
import com.devforge.security.model.VulnerabilityRecord;
import com.devforge.security.parse.ManifestException;
import com.devforge.security.parse.ManifestFinder;
import com.devforge.security.parse.ManifestFinder.Manifest;
import com.devforge.security.provider.AdvisoryJson;
import com.devforge.security.provider.AdvisoryProvider;
import com.devforge.security.provider.MetadataProvider;
import com.devforge.security.repo.ScanRepository;
import com.devforge.security.repo.ScanRepository.CachedAdvisories;
import com.devforge.security.repo.ScanRepository.CachedMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * DepGuard business logic. Reads manifests (never runs project tooling), checks exact versions
 * against advisory data, and records every scan. A scan only counts as clean when every dependency
 * was actually checked: unresolved versions or unavailable advisory data make it INCOMPLETE or UNAVAILABLE.
 */
public final class SecurityService {

    private static final String MODULE = "security";
    private static final int BREAKER_THRESHOLD = 3;

    private final Transactions tx;
    private final Configuration config;
    private final Clock clock;
    private final AdvisoryProvider advisories;
    private final MetadataProvider metadata;
    private final ScanRepository scans = new ScanRepository();
    private final ActivityLogRepository activity = new ActivityLogRepository();

    public SecurityService(
            ConnectionProvider provider, Configuration config, Clock clock, AdvisoryProvider advisories,
            MetadataProvider metadata) {
        this.tx = new Transactions(Objects.requireNonNull(provider, "provider"));
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.advisories = Objects.requireNonNull(advisories, "advisories");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    /** Stops calling a service after repeated consecutive failures so an outage does not stall the scan. */
    private static final class Breaker {
        private int failures;

        boolean open() {
            return failures >= BREAKER_THRESHOLD;
        }

        void success() {
            failures = 0;
        }

        void failure() {
            failures++;
        }
    }

    private record AdvisoryResult(CheckStatus status, List<Advisory> advisories, boolean fromCache) {}

    private record MetadataResult(PackageMetadata metadata, boolean ok) {}

    /**
     * Scans every supported manifest in the project and stores the results.
     *
     * @param offline use only cached advisory and registry data; make no network requests
     */
    public List<ManifestScan> scan(Project project, boolean offline) {
        Path root = Path.of(project.rootPath());
        if (!Files.isDirectory(root)) {
            throw new SecurityScanException("Project directory does not exist: " + root);
        }
        List<Manifest> manifests;
        try {
            manifests = ManifestFinder.find(root);
        } catch (IOException e) {
            throw new SecurityScanException("Could not read project directory " + root + ": " + e.getMessage(), e);
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Breaker advisoryBreaker = new Breaker();
        Breaker metadataBreaker = new Breaker();
        List<ManifestScan> results = new ArrayList<>();
        for (Manifest manifest : manifests) {
            results.add(scanManifest(project, manifest, offline, now, advisoryBreaker, metadataBreaker));
        }
        return results;
    }

    private ManifestScan scanManifest(
            Project project, Manifest manifest, boolean offline, Instant now, Breaker ab, Breaker mb) {
        List<Dependency> deps = List.of();
        List<String> notes = new ArrayList<>();
        boolean parseFailed = false;
        try {
            deps = ManifestFinder.parse(manifest);
        } catch (ManifestException e) {
            parseFailed = true;
            notes.add(e.getMessage());
        }

        record Item(Dependency dep, AdvisoryResult adv, MetadataResult meta) {}
        List<Item> items = new ArrayList<>();
        int checked = 0;
        int unresolved = 0;
        int unavailable = 0;
        int findings = 0;
        for (Dependency dep : deps) {
            AdvisoryResult adv = checkAdvisories(dep, offline, now, ab);
            MetadataResult meta = lookupMetadata(dep, offline, now, mb);
            items.add(new Item(dep, adv, meta));
            switch (adv.status()) {
                case CHECKED -> {
                    checked++;
                    findings += adv.advisories().size();
                }
                case UNRESOLVED -> unresolved++;
                case UNAVAILABLE -> unavailable++;
            }
        }
        if (unresolved > 0) {
            notes.add(unresolved + " dependenc" + (unresolved == 1 ? "y has" : "ies have")
                    + " no exact version, so advisories cannot be matched");
        }
        if (unavailable > 0) {
            notes.add(unavailable + " dependenc" + (unavailable == 1 ? "y" : "ies")
                    + " could not be checked because advisory data was unavailable");
        }

        ScanStatus status;
        if (!deps.isEmpty() && checked == 0 && unavailable > 0) {
            status = ScanStatus.UNAVAILABLE;
        } else if (parseFailed || unavailable > 0 || unresolved > 0) {
            status = ScanStatus.INCOMPLETE;
        } else if (findings > 0) {
            status = ScanStatus.COMPLETE_WITH_FINDINGS;
        } else {
            status = ScanStatus.COMPLETE;
        }
        String message = String.join("; ", notes);

        return tx.inTransaction("Could not store the scan results", c -> {
            long scanId = scans.insertScan(c, project.id(), manifest.ecosystem(), manifest.relativePath(), now,
                    status, message, offline);
            List<DependencyRecord> depRecords = new ArrayList<>();
            List<VulnerabilityRecord> vulnRecords = new ArrayList<>();
            for (Item item : items) {
                Dependency d = item.dep();
                PackageMetadata md = item.meta().metadata();
                String latest = md == null ? null : md.latestVersion();
                String license = md == null ? null : md.license();
                long depId = scans.insertDependency(c, scanId, d.name(), d.declaredVersion(), d.resolvedVersion(),
                        d.direct(), d.scope(), item.adv().status(), latest, license, item.meta().ok());
                depRecords.add(new DependencyRecord(depId, scanId, d.name(), d.declaredVersion(), d.resolvedVersion(),
                        d.direct(), d.scope(), item.adv().status(), latest, license, item.meta().ok()));
                for (Advisory a : item.adv().advisories()) {
                    scans.insertVulnerability(c, scanId, d.name(), d.resolvedVersion(), a, item.adv().fromCache());
                    vulnRecords.add(new VulnerabilityRecord(scanId, d.name(), d.resolvedVersion(), a.affectedRange(),
                            a.severity(), a.id(), a.fixedVersions(), a.summary(), a.references(), a.source(),
                            item.adv().fromCache()));
                }
            }
            activity.record(c, project.id(), MODULE, "scan", manifest.relativePath() + " " + status, now);
            return new ManifestScan(
                    new ScanRecord(scanId, project.id(), manifest.ecosystem(), manifest.relativePath(), now, status,
                            message, offline),
                    depRecords, vulnRecords);
        });
    }

    private AdvisoryResult checkAdvisories(Dependency dep, boolean offline, Instant now, Breaker breaker) {
        if (dep.resolvedVersion() == null) {
            return new AdvisoryResult(CheckStatus.UNRESOLVED, List.of(), false);
        }
        Optional<CachedAdvisories> cached = tx.read("Could not read the advisory cache",
                c -> scans.getAdvisories(c, dep.ecosystem(), dep.name(), dep.resolvedVersion()));
        Optional<List<Advisory>> cachedList = cached.flatMap(ca -> AdvisoryJson.fromJson(ca.json()));
        boolean fresh = cached.isPresent() && cachedList.isPresent() && config.advisoryCacheHours() > 0
                && now.isBefore(cached.get().fetchedAt().plus(config.advisoryCacheHours(), ChronoUnit.HOURS));

        if (cachedList.isPresent() && (offline || fresh)) {
            return new AdvisoryResult(CheckStatus.CHECKED, cachedList.get(), true);
        }
        if (offline || breaker.open()) {
            return cachedList
                    .map(list -> new AdvisoryResult(CheckStatus.CHECKED, list, true))
                    .orElseGet(() -> new AdvisoryResult(CheckStatus.UNAVAILABLE, List.of(), false));
        }
        try {
            List<Advisory> found = advisories.advisoriesFor(dep);
            breaker.success();
            tx.inTransaction("Could not update the advisory cache", c -> {
                scans.putAdvisories(c, dep.ecosystem(), dep.name(), dep.resolvedVersion(),
                        AdvisoryJson.toJson(found), now);
                return null;
            });
            return new AdvisoryResult(CheckStatus.CHECKED, found, false);
        } catch (IOException e) {
            breaker.failure();
            return cachedList
                    .map(list -> new AdvisoryResult(CheckStatus.CHECKED, list, true))
                    .orElseGet(() -> new AdvisoryResult(CheckStatus.UNAVAILABLE, List.of(), false));
        }
    }

    private MetadataResult lookupMetadata(Dependency dep, boolean offline, Instant now, Breaker breaker) {
        String key = dep.resolvedVersion() == null ? "-" : dep.resolvedVersion();
        Optional<CachedMetadata> cached = tx.read("Could not read the metadata cache",
                c -> scans.getMetadata(c, dep.ecosystem(), dep.name(), key));
        boolean fresh = cached.isPresent() && config.advisoryCacheHours() > 0
                && now.isBefore(cached.get().fetchedAt().plus(config.advisoryCacheHours(), ChronoUnit.HOURS));
        if (cached.isPresent() && (offline || fresh)) {
            return new MetadataResult(new PackageMetadata(cached.get().latestVersion(), cached.get().license()), true);
        }
        if (offline || breaker.open()) {
            return cached.map(m -> new MetadataResult(new PackageMetadata(m.latestVersion(), m.license()), true))
                    .orElseGet(() -> new MetadataResult(null, false));
        }
        try {
            PackageMetadata md = metadata.lookup(dep);
            breaker.success();
            tx.inTransaction("Could not update the metadata cache", c -> {
                scans.putMetadata(c, dep.ecosystem(), dep.name(), key, md.latestVersion(), md.license(), now);
                return null;
            });
            return new MetadataResult(md, true);
        } catch (IOException e) {
            breaker.failure();
            return cached.map(m -> new MetadataResult(new PackageMetadata(m.latestVersion(), m.license()), true))
                    .orElseGet(() -> new MetadataResult(null, false));
        }
    }

    /** The stored results of the project's most recent scan run (empty if never scanned). */
    public List<ManifestScan> latestScans(Project project) {
        return tx.read("Could not read scan results", c -> {
            List<ManifestScan> out = new ArrayList<>();
            for (ScanRecord s : scans.latestRun(c, project.id())) {
                out.add(new ManifestScan(s, scans.dependencies(c, s.id()), scans.vulnerabilities(c, s.id())));
            }
            return out;
        });
    }

    public com.devforge.security.model.SecurityReport report(Project project) {
        return SecurityReports.build(project, latestScans(project));
    }
}

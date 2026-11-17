package com.devforge.search.service;

import com.devforge.core.config.Configuration;
import com.devforge.core.db.ConnectionProvider;
import com.devforge.core.db.Transactions;
import com.devforge.core.hash.Sha256;
import com.devforge.core.project.ActivityLogRepository;
import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectRegistry;
import com.devforge.search.SearchException;
import com.devforge.search.model.CandidateFile;
import com.devforge.search.model.DuplicateGroup;
import com.devforge.search.model.DuplicateReport;
import com.devforge.search.model.IndexStatus;
import com.devforge.search.model.IndexSummary;
import com.devforge.search.model.IndexedFile;
import com.devforge.search.model.SearchHit;
import com.devforge.search.repo.FileIndexRepository;
import com.devforge.search.scan.IgnoreRules;
import com.devforge.search.scan.ProjectScanner;
import com.devforge.search.scan.ProjectScanner.ScanResult;
import com.devforge.search.scan.ProjectScanner.ScannedFile;
import com.devforge.search.scan.TextReader;
import com.devforge.search.scan.TextReader.TextFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * FindX business logic: incremental indexing, full rebuilds, search, status and duplicate detection.
 * Everything happens locally; nothing is uploaded. Files are never modified or deleted.
 */
public final class IndexService {

    private static final String MODULE = "search";
    public static final int MAX_LIMIT = 500;

    private final Transactions tx;
    private final ProjectRegistry projects;
    private final Configuration config;
    private final Clock clock;
    private final FileIndexRepository files = new FileIndexRepository();
    private final ActivityLogRepository activity = new ActivityLogRepository();

    public IndexService(ConnectionProvider provider, ProjectRegistry projects, Configuration config, Clock clock) {
        this.tx = new Transactions(Objects.requireNonNull(provider, "provider"));
        this.projects = Objects.requireNonNull(projects, "projects");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The given project (id or name), or the registered project containing {@code directory}. */
    public Project resolveProject(String reference, Path directory) {
        if (reference != null) {
            return projects.resolve(reference);
        }
        return projects.findContaining(directory).orElseThrow(() -> new SearchException(
                "No registered project contains " + directory.toAbsolutePath()
                        + ". Register it first with 'devforge init <dir>' or pass --project."));
    }

    /** Incremental index: adds new files, re-reads changed files and removes vanished files. */
    public IndexSummary index(Project project) {
        return run(project, false);
    }

    /** Drops the project's index and builds it again from scratch (full reconciliation). */
    public IndexSummary rebuild(Project project) {
        return run(project, true);
    }

    private IndexSummary run(Project project, boolean rebuild) {
        Path root = Path.of(project.rootPath());
        if (!Files.isDirectory(root)) {
            throw new SearchException("Project directory does not exist: " + root);
        }
        ScanResult scan;
        try {
            scan = ProjectScanner.scan(root, IgnoreRules.forProject(root, config.searchIgnorePaths()));
        } catch (IOException e) {
            throw new SearchException("Could not read project directory " + root + ": " + e.getMessage());
        }
        long maxBytes = config.searchMaxFileSizeBytes();
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);

        return tx.inTransaction("Could not update the search index", c -> {
            if (rebuild) {
                files.deleteProject(c, project.id());
            }
            Map<String, IndexedFile> existing = files.findAllByProject(c, project.id());
            Set<String> seen = new HashSet<>();
            int added = 0;
            int updated = 0;
            int unchanged = 0;
            int contentIndexed = 0;
            int unreadable = scan.unreadable();

            for (ScannedFile f : scan.files()) {
                seen.add(f.relativePath());
                IndexedFile old = existing.get(f.relativePath());
                if (old != null && old.size() == f.size() && old.modifiedAt().equals(f.modifiedAt())) {
                    unchanged++;
                    if (old.contentIndexed()) {
                        contentIndexed++;
                    }
                    continue;
                }
                Optional<TextFile> text;
                try {
                    text = TextReader.read(f.absolutePath(), f.size(), maxBytes);
                } catch (IOException e) {
                    unreadable++;
                    continue;
                }
                String hash = text.map(TextFile::sha256).orElse(null);
                boolean indexed = text.isPresent();
                long id;
                if (old == null) {
                    id = files.insert(c, project.id(), f.relativePath(), f.extension(), f.size(), hash,
                            indexed, f.modifiedAt(), now);
                    added++;
                } else {
                    id = old.id();
                    files.update(c, id, f.size(), hash, indexed, f.modifiedAt(), now);
                    files.deleteContent(c, id);
                    updated++;
                }
                if (indexed) {
                    files.insertContent(c, id, f.relativePath(), text.get().content());
                    contentIndexed++;
                }
            }
            int removed = 0;
            for (Map.Entry<String, IndexedFile> e : existing.entrySet()) {
                if (!seen.contains(e.getKey())) {
                    files.deleteContent(c, e.getValue().id());
                    files.delete(c, e.getValue().id());
                    removed++;
                }
            }
            files.purgeOrphanContent(c);
            activity.record(c, project.id(), MODULE, rebuild ? "index.rebuild" : "index.update",
                    "added=" + added + " updated=" + updated + " removed=" + removed, now);
            return new IndexSummary(added, updated, removed, unchanged, contentIndexed, unreadable);
        });
    }

    public IndexStatus status(Project project) {
        FileIndexRepository.Totals t = tx.read("Could not read the index status",
                c -> files.totals(c, project.id()));
        return new IndexStatus(project.id(), project.name(), t.files(), t.contentIndexed(), t.bytes(), t.lastIndexedAt());
    }

    /**
     * Searches file contents (ranked) and file paths. Results from file contents come first.
     *
     * @param projectId null searches all projects
     * @param extension optional extension filter, with or without a leading dot
     */
    public List<SearchHit> search(String text, Long projectId, String extension, int limit) {
        String fts = FtsQuery.build(text);
        int cap = Math.max(1, Math.min(limit, MAX_LIMIT));
        String ext = extension == null || extension.isBlank()
                ? null
                : extension.strip().replaceFirst("^\\.", "").toLowerCase(Locale.ROOT);
        Map<Long, String> names = projectNames();

        return tx.read("Could not search the index", c -> {
            List<SearchHit> hits = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (FileIndexRepository.Row r : files.searchContent(c, fts, projectId, ext, cap)) {
                if (seen.add(r.projectId() + ":" + r.path())) {
                    hits.add(new SearchHit(r.projectId(), names.getOrDefault(r.projectId(), "?"), r.path(),
                            "content", r.snippet()));
                }
            }
            if (hits.size() < cap) {
                for (FileIndexRepository.Row r : files.searchNames(c, text.strip(), projectId, ext, cap)) {
                    if (hits.size() < cap && seen.add(r.projectId() + ":" + r.path())) {
                        hits.add(new SearchHit(r.projectId(), names.getOrDefault(r.projectId(), "?"), r.path(),
                                "name", ""));
                    }
                }
            }
            return hits;
        });
    }

    /**
     * Finds files with identical content: candidates are grouped by size, hashed with SHA-256 and
     * compared. Nothing is ever deleted. Hashes of text files come from the last index run.
     */
    public DuplicateReport duplicates(Long projectId) {
        List<CandidateFile> candidates = tx.read("Could not read the file index",
                c -> files.sizeCandidates(c, projectId));
        Map<Long, Project> byId = new HashMap<>();
        for (Project p : projects.list()) {
            byId.put(p.id(), p);
        }

        Map<Long, String> newHashes = new LinkedHashMap<>();
        Map<String, List<CandidateFile>> byHash = new LinkedHashMap<>();
        int unreadable = 0;
        for (CandidateFile cand : candidates) {
            String hash = cand.hash();
            if (hash == null) {
                Project p = byId.get(cand.projectId());
                if (p == null) {
                    unreadable++;
                    continue;
                }
                try {
                    hash = Sha256.file(Path.of(p.rootPath()).resolve(cand.path()));
                } catch (IOException e) {
                    unreadable++;
                    continue;
                }
                newHashes.put(cand.id(), hash);
            }
            byHash.computeIfAbsent(cand.size() + ":" + hash, k -> new ArrayList<>()).add(cand);
        }
        if (!newHashes.isEmpty()) {
            tx.inTransaction("Could not store file hashes", c -> {
                for (Map.Entry<Long, String> e : newHashes.entrySet()) {
                    files.setHash(c, e.getKey(), e.getValue());
                }
                return null;
            });
        }

        List<DuplicateGroup> groups = new ArrayList<>();
        long wasted = 0;
        for (Map.Entry<String, List<CandidateFile>> e : byHash.entrySet()) {
            List<CandidateFile> group = e.getValue();
            if (group.size() < 2) {
                continue;
            }
            CandidateFile first = group.get(0);
            List<String> locations = new ArrayList<>();
            for (CandidateFile f : group) {
                Project p = byId.get(f.projectId());
                locations.add((p == null ? "?" : p.name()) + ":" + f.path());
            }
            String hash = e.getKey().substring(e.getKey().indexOf(':') + 1);
            groups.add(new DuplicateGroup(hash, first.size(), locations));
            wasted += first.size() * (group.size() - 1);
        }
        return new DuplicateReport(groups, unreadable, wasted);
    }

    private Map<Long, String> projectNames() {
        Map<Long, String> names = new HashMap<>();
        for (Project p : projects.list()) {
            names.put(p.id(), p.name());
        }
        return names;
    }
}

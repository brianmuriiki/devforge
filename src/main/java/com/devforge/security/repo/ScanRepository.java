package com.devforge.security.repo;

import com.devforge.security.model.Advisory;
import com.devforge.security.model.CheckStatus;
import com.devforge.security.model.DependencyRecord;
import com.devforge.security.model.Ecosystem;
import com.devforge.security.model.ScanRecord;
import com.devforge.security.model.ScanStatus;
import com.devforge.security.model.Severity;
import com.devforge.security.model.VulnerabilityRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** SQL access for scans, dependencies, vulnerabilities and the two caches. */
public final class ScanRepository {

    public record CachedAdvisories(String json, Instant fetchedAt) {}

    public record CachedMetadata(String latestVersion, String license, Instant fetchedAt) {}

    private static final String SEP = "\n";

    public long insertScan(
            Connection c, long projectId, Ecosystem eco, String manifestPath, Instant at, ScanStatus status,
            String message, boolean offline) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO dependency_scans (project_id, ecosystem, manifest_path, scanned_at, scan_status, "
                        + "message, offline) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, projectId);
            ps.setString(2, eco.name());
            ps.setString(3, manifestPath);
            ps.setString(4, at.toString());
            ps.setString(5, status.name());
            ps.setString(6, message);
            ps.setInt(7, offline ? 1 : 0);
            ps.executeUpdate();
        }
        return lastId(c);
    }

    public long insertDependency(
            Connection c, long scanId, String name, String declared, String resolved, boolean direct, String scope,
            CheckStatus check, String latest, String license, boolean metadataOk) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO dependencies (scan_id, name, declared_version, resolved_version, direct, scope, "
                        + "check_status, latest_version, license, metadata_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, scanId);
            ps.setString(2, name);
            ps.setString(3, declared);
            nullable(ps, 4, resolved);
            ps.setInt(5, direct ? 1 : 0);
            ps.setString(6, scope);
            ps.setString(7, check.name());
            nullable(ps, 8, latest);
            nullable(ps, 9, license);
            ps.setString(10, metadataOk ? "OK" : "UNAVAILABLE");
            ps.executeUpdate();
        }
        return lastId(c);
    }

    public void insertVulnerability(
            Connection c, long scanId, String packageName, String version, Advisory a, boolean fromCache)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO vulnerabilities (scan_id, package_name, installed_version, affected_range, severity, "
                        + "advisory_id, fixed_versions, description, reference_urls, source, from_cache) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, scanId);
            ps.setString(2, packageName);
            ps.setString(3, version);
            ps.setString(4, a.affectedRange());
            ps.setString(5, a.severity().name());
            ps.setString(6, a.id());
            ps.setString(7, String.join(SEP, a.fixedVersions()));
            ps.setString(8, a.summary());
            ps.setString(9, String.join(SEP, a.references()));
            ps.setString(10, a.source());
            ps.setInt(11, fromCache ? 1 : 0);
            ps.executeUpdate();
        }
    }

    /** The scans of the project's most recent run: the newest scan of each manifest at the latest timestamp. */
    public List<ScanRecord> latestRun(Connection c, long projectId) throws SQLException {
        List<ScanRecord> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, project_id, ecosystem, manifest_path, scanned_at, scan_status, message, offline "
                        + "FROM dependency_scans WHERE id IN (SELECT MAX(id) FROM dependency_scans "
                        + "WHERE project_id = ? AND scanned_at = (SELECT MAX(scanned_at) FROM dependency_scans "
                        + "WHERE project_id = ?) GROUP BY manifest_path) ORDER BY manifest_path")) {
            ps.setLong(1, projectId);
            ps.setLong(2, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ScanRecord(rs.getLong(1), rs.getLong(2), Ecosystem.valueOf(rs.getString(3)),
                            rs.getString(4), Instant.parse(rs.getString(5)), ScanStatus.valueOf(rs.getString(6)),
                            rs.getString(7), rs.getInt(8) == 1));
                }
            }
        }
        return out;
    }

    public List<DependencyRecord> dependencies(Connection c, long scanId) throws SQLException {
        List<DependencyRecord> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, scan_id, name, declared_version, resolved_version, direct, scope, check_status, "
                        + "latest_version, license, metadata_status FROM dependencies WHERE scan_id = ? ORDER BY id")) {
            ps.setLong(1, scanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DependencyRecord(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getInt(6) == 1, rs.getString(7), CheckStatus.valueOf(rs.getString(8)),
                            rs.getString(9), rs.getString(10), "OK".equals(rs.getString(11))));
                }
            }
        }
        return out;
    }

    public List<VulnerabilityRecord> vulnerabilities(Connection c, long scanId) throws SQLException {
        List<VulnerabilityRecord> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT scan_id, package_name, installed_version, affected_range, severity, advisory_id, "
                        + "fixed_versions, description, reference_urls, source, from_cache "
                        + "FROM vulnerabilities WHERE scan_id = ? ORDER BY id")) {
            ps.setLong(1, scanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new VulnerabilityRecord(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            Severity.valueOf(rs.getString(5)), rs.getString(6), split(rs.getString(7)), rs.getString(8),
                            split(rs.getString(9)), rs.getString(10), rs.getInt(11) == 1));
                }
            }
        }
        return out;
    }

    public Optional<CachedAdvisories> getAdvisories(Connection c, Ecosystem eco, String name, String version)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT advisories_json, fetched_at FROM advisory_cache "
                        + "WHERE ecosystem = ? AND package_name = ? AND version = ?")) {
            ps.setString(1, eco.name());
            ps.setString(2, name);
            ps.setString(3, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new CachedAdvisories(rs.getString(1), Instant.parse(rs.getString(2))))
                        : Optional.empty();
            }
        }
    }

    public void putAdvisories(Connection c, Ecosystem eco, String name, String version, String json, Instant at)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR REPLACE INTO advisory_cache (ecosystem, package_name, version, advisories_json, fetched_at) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, eco.name());
            ps.setString(2, name);
            ps.setString(3, version);
            ps.setString(4, json);
            ps.setString(5, at.toString());
            ps.executeUpdate();
        }
    }

    public Optional<CachedMetadata> getMetadata(Connection c, Ecosystem eco, String name, String version)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT latest_version, license, fetched_at FROM package_metadata_cache "
                        + "WHERE ecosystem = ? AND package_name = ? AND version = ?")) {
            ps.setString(1, eco.name());
            ps.setString(2, name);
            ps.setString(3, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new CachedMetadata(rs.getString(1), rs.getString(2), Instant.parse(rs.getString(3))))
                        : Optional.empty();
            }
        }
    }

    public void putMetadata(
            Connection c, Ecosystem eco, String name, String version, String latest, String license, Instant at)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR REPLACE INTO package_metadata_cache (ecosystem, package_name, version, latest_version, "
                        + "license, fetched_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, eco.name());
            ps.setString(2, name);
            ps.setString(3, version);
            nullable(ps, 4, latest);
            nullable(ps, 5, license);
            ps.setString(6, at.toString());
            ps.executeUpdate();
        }
    }

    private static long lastId(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void nullable(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static List<String> split(String joined) {
        return joined == null || joined.isEmpty() ? List.of() : Arrays.asList(joined.split(SEP));
    }
}

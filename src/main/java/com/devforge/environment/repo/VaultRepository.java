package com.devforge.environment.repo;

import com.devforge.environment.model.BackupRecord;
import com.devforge.environment.model.VaultProfile;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQL access for DevVault profiles, profile paths and backups. */
public final class VaultRepository {

    private static final String BACKUP_SELECT =
            "SELECT b.id, b.profile_id, p.name, b.name, b.archive_path, b.manifest_path, b.checksum, b.created_at, "
                    + "b.verified, b.verified_at, b.size_bytes, b.file_count, b.app_version, b.includes_sensitive "
                    + "FROM vault_backups b LEFT JOIN vault_profiles p ON p.id = b.profile_id";

    public long insertProfile(Connection c, String name, String description, String destination, Instant now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO vault_profiles (name, description, destination, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, name);
            ps.setString(2, description);
            if (destination == null) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, destination);
            }
            ps.setString(4, now.toString());
            ps.setString(5, now.toString());
            ps.executeUpdate();
        }
        return lastId(c);
    }

    public void insertPath(Connection c, long profileId, String kind, String pattern) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO vault_profile_paths (profile_id, kind, pattern) VALUES (?, ?, ?)")) {
            ps.setLong(1, profileId);
            ps.setString(2, kind);
            ps.setString(3, pattern);
            ps.executeUpdate();
        }
    }

    public Optional<VaultProfile> findProfile(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, name, description, destination, created_at, updated_at FROM vault_profiles WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(profile(c, rs)) : Optional.empty();
            }
        }
    }

    public List<VaultProfile> listProfiles(Connection c) throws SQLException {
        List<VaultProfile> out = new ArrayList<>();
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id, name, description, destination, created_at, updated_at FROM vault_profiles ORDER BY name")) {
            while (rs.next()) {
                out.add(profile(c, rs));
            }
        }
        return out;
    }

    /** Deletes the profile definition only; backups made from it stay registered and on disk. */
    public boolean deleteProfile(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM vault_profiles WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    public long insertBackup(
            Connection c, Long profileId, String name, String archivePath, String manifestPath, String checksum,
            Instant createdAt, boolean verified, long sizeBytes, int fileCount, String appVersion, boolean includesSensitive)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO vault_backups (profile_id, name, archive_path, manifest_path, checksum, created_at, "
                        + "verified, verified_at, size_bytes, file_count, app_version, includes_sensitive) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            if (profileId == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setLong(1, profileId);
            }
            ps.setString(2, name);
            ps.setString(3, archivePath);
            ps.setString(4, manifestPath);
            ps.setString(5, checksum);
            ps.setString(6, createdAt.toString());
            ps.setInt(7, verified ? 1 : 0);
            ps.setString(8, verified ? createdAt.toString() : null);
            ps.setLong(9, sizeBytes);
            ps.setInt(10, fileCount);
            ps.setString(11, appVersion);
            ps.setInt(12, includesSensitive ? 1 : 0);
            ps.executeUpdate();
        }
        return lastId(c);
    }

    public List<BackupRecord> listBackups(Connection c) throws SQLException {
        List<BackupRecord> out = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(BACKUP_SELECT + " ORDER BY b.id")) {
            while (rs.next()) {
                out.add(backup(rs));
            }
        }
        return out;
    }

    public Optional<BackupRecord> findBackup(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(BACKUP_SELECT + " WHERE b.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(backup(rs)) : Optional.empty();
            }
        }
    }

    public Optional<BackupRecord> latestBackup(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(BACKUP_SELECT + " ORDER BY b.id DESC LIMIT 1")) {
            return rs.next() ? Optional.of(backup(rs)) : Optional.empty();
        }
    }

    public void markVerified(Connection c, long id, boolean verified, Instant at) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE vault_backups SET verified = ?, verified_at = ? WHERE id = ?")) {
            ps.setInt(1, verified ? 1 : 0);
            ps.setString(2, at.toString());
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    private VaultProfile profile(Connection c, ResultSet rs) throws SQLException {
        long id = rs.getLong(1);
        return new VaultProfile(id, rs.getString(2), rs.getString(3), rs.getString(4), paths(c, id, "INCLUDE"),
                paths(c, id, "EXCLUDE"), Instant.parse(rs.getString(5)), Instant.parse(rs.getString(6)));
    }

    private static List<String> paths(Connection c, long profileId, String kind) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT pattern FROM vault_profile_paths WHERE profile_id = ? AND kind = ? ORDER BY id")) {
            ps.setLong(1, profileId);
            ps.setString(2, kind);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    private static BackupRecord backup(ResultSet rs) throws SQLException {
        long profileId = rs.getLong(2);
        Long profile = rs.wasNull() ? null : profileId;
        String verifiedAt = rs.getString(10);
        return new BackupRecord(rs.getLong(1), profile, rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), Instant.parse(rs.getString(8)), rs.getInt(9) == 1,
                verifiedAt == null ? null : Instant.parse(verifiedAt), rs.getLong(11), rs.getInt(12), rs.getString(13),
                rs.getInt(14) == 1);
    }

    private static long lastId(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}

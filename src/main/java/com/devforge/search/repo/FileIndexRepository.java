package com.devforge.search.repo;

import com.devforge.search.model.CandidateFile;
import com.devforge.search.model.IndexedFile;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** SQL access for {@code file_index} and the {@code file_contents} FTS5 table. */
public final class FileIndexRepository {

    /** One raw search row. */
    public record Row(long projectId, String path, String snippet) {}

    /** Per-project totals. {@code lastIndexedAt} is null when there are no rows. */
    public record Totals(int files, int contentIndexed, long bytes, Instant lastIndexedAt) {}

    public Map<String, IndexedFile> findAllByProject(Connection c, long projectId) throws SQLException {
        Map<String, IndexedFile> map = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, file_path, file_size, content_hash, content_indexed, modified_at "
                        + "FROM file_index WHERE project_id = ?")) {
            ps.setLong(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString(2), new IndexedFile(
                            rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                            rs.getInt(5) == 1, Instant.parse(rs.getString(6))));
                }
            }
        }
        return map;
    }

    public long insert(
            Connection c, long projectId, String path, String extension, long size, String hash,
            boolean contentIndexed, Instant modifiedAt, Instant indexedAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO file_index (project_id, file_path, extension, file_size, content_hash, "
                        + "content_indexed, modified_at, indexed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, projectId);
            ps.setString(2, path);
            ps.setString(3, extension);
            ps.setLong(4, size);
            ps.setString(5, hash);
            ps.setInt(6, contentIndexed ? 1 : 0);
            ps.setString(7, modifiedAt.toString());
            ps.setString(8, indexedAt.toString());
            ps.executeUpdate();
        }
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public void update(
            Connection c, long id, long size, String hash, boolean contentIndexed,
            Instant modifiedAt, Instant indexedAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE file_index SET file_size = ?, content_hash = ?, content_indexed = ?, "
                        + "modified_at = ?, indexed_at = ? WHERE id = ?")) {
            ps.setLong(1, size);
            ps.setString(2, hash);
            ps.setInt(3, contentIndexed ? 1 : 0);
            ps.setString(4, modifiedAt.toString());
            ps.setString(5, indexedAt.toString());
            ps.setLong(6, id);
            ps.executeUpdate();
        }
    }

    public void setHash(Connection c, long id, String hash) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE file_index SET content_hash = ? WHERE id = ?")) {
            ps.setString(1, hash);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void delete(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM file_index WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public void insertContent(Connection c, long id, String path, String content) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO file_contents (rowid, file_path, content) VALUES (?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, path);
            ps.setString(3, content);
            ps.executeUpdate();
        }
    }

    public void deleteContent(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM file_contents WHERE rowid = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    /** Removes FTS rows whose file_index row is gone (for example after a project was removed). */
    public void purgeOrphanContent(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM file_contents WHERE rowid NOT IN (SELECT id FROM file_index)");
        }
    }

    /** Deletes every index row of a project, including its searchable content. */
    public void deleteProject(Connection c, long projectId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM file_contents WHERE rowid IN (SELECT id FROM file_index WHERE project_id = ?)")) {
            ps.setLong(1, projectId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM file_index WHERE project_id = ?")) {
            ps.setLong(1, projectId);
            ps.executeUpdate();
        }
    }

    public Totals totals(Connection c, long projectId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*), COALESCE(SUM(content_indexed), 0), COALESCE(SUM(file_size), 0), "
                        + "MAX(indexed_at) FROM file_index WHERE project_id = ?")) {
            ps.setLong(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                String last = rs.getString(4);
                return new Totals(rs.getInt(1), rs.getInt(2), rs.getLong(3), last == null ? null : Instant.parse(last));
            }
        }
    }

    public List<Row> searchContent(Connection c, String ftsQuery, Long projectId, String extension, int limit)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT f.project_id, f.file_path, snippet(file_contents, 1, '[', ']', '...', 12) "
                        + "FROM file_contents JOIN file_index f ON f.id = file_contents.rowid "
                        + "WHERE file_contents MATCH ?");
        List<Object> params = new ArrayList<>();
        params.add(ftsQuery);
        appendFilters(sql, params, projectId, extension);
        sql.append(" ORDER BY file_contents.rank LIMIT ?");
        params.add(limit);
        return rows(c, sql.toString(), params, true);
    }

    public List<Row> searchNames(Connection c, String text, Long projectId, String extension, int limit)
            throws SQLException {
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        StringBuilder sql = new StringBuilder(
                "SELECT f.project_id, f.file_path FROM file_index f WHERE f.file_path LIKE ? ESCAPE '\\'");
        List<Object> params = new ArrayList<>();
        params.add("%" + escaped + "%");
        appendFilters(sql, params, projectId, extension);
        sql.append(" ORDER BY f.file_path LIMIT ?");
        params.add(limit);
        return rows(c, sql.toString(), params, false);
    }

    /** Files whose size is shared with at least one other file (size 0 excluded). */
    public List<CandidateFile> sizeCandidates(Connection c, Long projectId) throws SQLException {
        String scope = projectId == null ? "" : " AND project_id = ?";
        String sql = "SELECT id, project_id, file_path, file_size, content_hash FROM file_index "
                + "WHERE file_size > 0" + scope + " AND file_size IN (SELECT file_size FROM file_index "
                + "WHERE file_size > 0" + scope + " GROUP BY file_size HAVING COUNT(*) > 1) "
                + "ORDER BY file_size, id";
        List<CandidateFile> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (projectId != null) {
                ps.setLong(1, projectId);
                ps.setLong(2, projectId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CandidateFile(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5)));
                }
            }
        }
        return out;
    }

    private static void appendFilters(StringBuilder sql, List<Object> params, Long projectId, String extension) {
        if (projectId != null) {
            sql.append(" AND f.project_id = ?");
            params.add(projectId);
        }
        if (extension != null) {
            sql.append(" AND f.extension = ?");
            params.add(extension);
        }
    }

    private static List<Row> rows(Connection c, String sql, List<Object> params, boolean hasSnippet)
            throws SQLException {
        List<Row> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Row(rs.getLong(1), rs.getString(2), hasSnippet ? rs.getString(3) : ""));
                }
            }
        }
        return out;
    }
}

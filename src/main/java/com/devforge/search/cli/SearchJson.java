package com.devforge.search.cli;

import com.devforge.search.model.DuplicateGroup;
import com.devforge.search.model.DuplicateReport;
import com.devforge.search.model.IndexStatus;
import com.devforge.search.model.IndexSummary;
import com.devforge.search.model.SearchHit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON shapes for FindX output (part of the CLI contract; also used by the combined report). */
public final class SearchJson {

    private SearchJson() {}

    public static Map<String, Object> hit(SearchHit h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("projectId", h.projectId());
        m.put("project", h.project());
        m.put("path", h.path());
        m.put("matchType", h.matchType());
        m.put("snippet", h.snippet());
        return m;
    }

    public static Map<String, Object> summary(IndexSummary s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("added", s.added());
        m.put("updated", s.updated());
        m.put("removed", s.removed());
        m.put("unchanged", s.unchanged());
        m.put("contentIndexed", s.contentIndexed());
        m.put("unreadable", s.unreadable());
        return m;
    }

    public static Map<String, Object> status(IndexStatus s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("projectId", s.projectId());
        m.put("project", s.project());
        m.put("files", s.files());
        m.put("contentIndexed", s.contentIndexed());
        m.put("totalBytes", s.totalBytes());
        m.put("lastIndexedAt", s.lastIndexedAt() == null ? null : s.lastIndexedAt().toString());
        return m;
    }

    public static Map<String, Object> duplicates(DuplicateReport r) {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (DuplicateGroup g : r.groups()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sha256", g.sha256());
            m.put("size", g.size());
            m.put("locations", g.locations());
            groups.add(m);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("groupCount", r.groups().size());
        m.put("wastedBytes", r.wastedBytes());
        m.put("unreadable", r.unreadable());
        m.put("groups", groups);
        return m;
    }
}

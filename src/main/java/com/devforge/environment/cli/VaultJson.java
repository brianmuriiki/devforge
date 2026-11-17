package com.devforge.environment.cli;

import com.devforge.environment.model.BackupRecord;
import com.devforge.environment.model.VaultProfile;
import java.util.LinkedHashMap;
import java.util.Map;

/** JSON shapes for DevVault output (part of the CLI contract; also used by the combined report). */
public final class VaultJson {

    private VaultJson() {}

    public static Map<String, Object> profile(VaultProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("name", p.name());
        m.put("description", p.description());
        m.put("destination", p.destination());
        m.put("includes", p.includes());
        m.put("excludes", p.excludes());
        m.put("createdAt", p.createdAt().toString());
        m.put("updatedAt", p.updatedAt().toString());
        return m;
    }

    public static Map<String, Object> backup(BackupRecord b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.id());
        m.put("name", b.name());
        m.put("profile", b.profileName());
        m.put("archivePath", b.archivePath());
        m.put("manifestPath", b.manifestPath());
        m.put("sha256", b.checksum());
        m.put("createdAt", b.createdAt().toString());
        m.put("verified", b.verified());
        m.put("verifiedAt", b.verifiedAt() == null ? null : b.verifiedAt().toString());
        m.put("sizeBytes", b.sizeBytes());
        m.put("fileCount", b.fileCount());
        m.put("appVersion", b.appVersion());
        m.put("includesSensitive", b.includesSensitive());
        m.put("encrypted", b.encrypted());
        return m;
    }
}

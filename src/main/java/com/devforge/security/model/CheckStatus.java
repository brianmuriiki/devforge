package com.devforge.security.model;

/** Whether advisory data was obtained for a dependency. */
public enum CheckStatus {
    /** Advisory data was obtained (fresh or cached). */
    CHECKED,
    /** No exact version is known, so advisories cannot be matched. */
    UNRESOLVED,
    /** Advisory data could not be obtained (offline without cache, or the service was unreachable). */
    UNAVAILABLE
}

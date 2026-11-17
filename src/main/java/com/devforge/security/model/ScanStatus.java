package com.devforge.security.model;

/**
 * Outcome of scanning one manifest. Only {@link #COMPLETE} means "checked everything, found nothing".
 * {@link #INCOMPLETE} and {@link #UNAVAILABLE} must never be presented as "secure".
 */
public enum ScanStatus {
    COMPLETE,
    COMPLETE_WITH_FINDINGS,
    INCOMPLETE,
    UNAVAILABLE
}

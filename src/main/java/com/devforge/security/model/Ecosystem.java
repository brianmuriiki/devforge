package com.devforge.security.model;

public enum Ecosystem {
    MAVEN("Maven"),
    NPM("npm"),
    PYPI("PyPI");

    private final String osvName;

    Ecosystem(String osvName) {
        this.osvName = osvName;
    }

    /** The ecosystem name used by OSV.dev. */
    public String osvName() {
        return osvName;
    }
}

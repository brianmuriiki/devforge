package com.devforge.security.provider;

import com.devforge.security.model.Dependency;
import com.devforge.security.model.PackageMetadata;
import java.io.IOException;

/** A source of registry metadata (latest version, license). */
public interface MetadataProvider {

    PackageMetadata lookup(Dependency dependency) throws IOException;
}

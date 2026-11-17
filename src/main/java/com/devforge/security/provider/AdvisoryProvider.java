package com.devforge.security.provider;

import com.devforge.security.model.Advisory;
import com.devforge.security.model.Dependency;
import java.io.IOException;
import java.util.List;

/** A source of vulnerability advisories. The architecture supports several providers. */
public interface AdvisoryProvider {

    String name();

    /** @param dependency must have a non-null resolved version */
    List<Advisory> advisoriesFor(Dependency dependency) throws IOException;
}

package com.devforge.search.cli;

import com.devforge.core.AppContext;
import com.devforge.search.service.IndexService;

/** Builds the FindX service from the shared context. */
final class SearchServices {

    private SearchServices() {}

    static IndexService create(AppContext context) {
        return new IndexService(
                context.connectionProvider(), context.projectRegistry(), context.config(), context.clock());
    }
}

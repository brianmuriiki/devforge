package com.devforge.security.provider;

import java.io.IOException;

/** Minimal HTTPS client used by DepGuard; replaceable in tests. */
public interface HttpTransport {

    String get(String url) throws IOException;

    String post(String url, String jsonBody) throws IOException;
}

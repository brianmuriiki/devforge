package com.devforge.core.paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OperatingSystemTest {

    @ParameterizedTest
    @CsvSource({
        "Linux,LINUX",
        "Windows 11,WINDOWS",
        "Windows 10,WINDOWS",
        "Mac OS X,MACOS",
        "Darwin,MACOS",
        "FreeBSD,LINUX"
    })
    void mapsOsNames(String osName, OperatingSystem expected) {
        assertEquals(expected, OperatingSystem.fromOsName(osName));
    }
}

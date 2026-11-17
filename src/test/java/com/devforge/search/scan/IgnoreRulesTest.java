package com.devforge.search.scan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IgnoreRulesTest {

    @Test
    void wildcardMatchesAtAnyDepth() {
        IgnoreRules r = IgnoreRules.parse(List.of("*.log"));
        assertTrue(r.isIgnored("a.log", false));
        assertTrue(r.isIgnored("dir/sub/a.log", false));
        assertFalse(r.isIgnored("a.txt", false));
        assertFalse(r.isIgnored("log", false));
    }

    @Test
    void trailingSlashMatchesDirectoriesOnly() {
        IgnoreRules r = IgnoreRules.parse(List.of("build/"));
        assertTrue(r.isIgnored("build", true));
        assertTrue(r.isIgnored("module/build", true));
        assertFalse(r.isIgnored("build", false));
    }

    @Test
    void leadingSlashAnchorsToRoot() {
        IgnoreRules r = IgnoreRules.parse(List.of("/root.txt"));
        assertTrue(r.isIgnored("root.txt", false));
        assertFalse(r.isIgnored("sub/root.txt", false));
    }

    @Test
    void patternsWithSlashAreAnchored() {
        IgnoreRules r = IgnoreRules.parse(List.of("docs/*.md"));
        assertTrue(r.isIgnored("docs/a.md", false));
        assertFalse(r.isIgnored("docs/sub/a.md", false));
        assertFalse(r.isIgnored("x/docs/a.md", false));
    }

    @Test
    void doubleStarMatchesAnyDepth() {
        IgnoreRules r = IgnoreRules.parse(List.of("**/temp"));
        assertTrue(r.isIgnored("temp", true));
        assertTrue(r.isIgnored("a/b/temp", true));
        assertFalse(r.isIgnored("a/b/temporary", true));
    }

    @Test
    void lastMatchingRuleWinsSoNegationWorks() {
        IgnoreRules r = IgnoreRules.parse(List.of("*.log", "!keep.log"));
        assertTrue(r.isIgnored("debug.log", false));
        assertFalse(r.isIgnored("keep.log", false));
    }

    @Test
    void commentsAndBlankLinesAreIgnored() {
        IgnoreRules r = IgnoreRules.parse(List.of("# a comment", "", "   ", "secret.txt"));
        assertTrue(r.isIgnored("secret.txt", false));
        assertFalse(r.isIgnored("# a comment", false));
    }

    @Test
    void regexCharactersInPatternsAreLiteral() {
        IgnoreRules r = IgnoreRules.parse(List.of("a+b(1).txt"));
        assertTrue(r.isIgnored("a+b(1).txt", false));
        assertFalse(r.isIgnored("aab1.txt", false));
    }

    @Test
    void emptyRulesIgnoreNothing() {
        assertFalse(IgnoreRules.empty().isIgnored("anything", false));
    }
}

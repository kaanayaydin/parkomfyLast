package com.parkomfy.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class PlateMatcherTest {

    @Test
    public void normalize_stripsSpacesAndUppercases() {
        assertEquals("34PGZ545", PlateMatcher.normalize("34 pgz 545"));
        assertEquals("67ACP810", PlateMatcher.normalize("67-ACP-810"));
        assertEquals("", PlateMatcher.normalize(null));
    }

    @Test
    public void similarity_exactMatchIsOne() {
        assertEquals(1.0, PlateMatcher.similarity("34 PGZ 545", "34PGZ545"), 0.001);
    }

    @Test
    public void similarity_ocrTypoIsHigh() {
        double score = PlateMatcher.similarity("34 P5Z 545", "34 PGZ 545");
        assertTrue(score >= 0.85);
    }

    @Test
    public void matches_respectsThreshold() {
        assertTrue(PlateMatcher.matches("67ACP810", "67 ACP 810", 0.75));
        assertFalse(PlateMatcher.matches("34ABC123", "67ACP810", 0.75));
    }

    @Test
    public void findBestMatch_picksClosestCandidate() {
        List<String> candidates = Arrays.asList("34 PGZ 545", "67 ACP 810");
        PlateMatcher.MatchResult<String> result = PlateMatcher.findBestMatch(
            "67ACP810",
            candidates,
            s -> s,
            0.75
        );
        assertNotNull(result);
        assertEquals("67ACP810", result.getMatchedPlate());
        assertEquals("67 ACP 810", result.getItem());
    }

    @Test
    public void findBestMatch_returnsNullBelowThreshold() {
        List<String> candidates = Arrays.asList("34 PGZ 545", "67 ACP 810");
        assertNull(PlateMatcher.findBestMatch("99ZZZ999", candidates, s -> s, 0.75));
    }
}

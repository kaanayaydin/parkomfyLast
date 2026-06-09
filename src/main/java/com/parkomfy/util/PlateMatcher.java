package com.parkomfy.util;

import java.util.List;
import java.util.function.Function;

/**
 * Fuzzy license plate matching for OCR tolerance.
 */
public final class PlateMatcher {

    public static final double DEFAULT_MATCH_THRESHOLD = 0.75;

    private PlateMatcher() {}

    public static String normalize(String plate) {
        if (plate == null) return "";
        return plate.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    public static double similarity(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) return 0.0;
        if (na.equals(nb)) return 1.0;
        int dist = levenshtein(na, nb);
        int maxLen = Math.max(na.length(), nb.length());
        return 1.0 - ((double) dist / maxLen);
    }

    public static boolean matches(String detected, String expected, double threshold) {
        return similarity(detected, expected) >= threshold;
    }

    /**
     * Pick best candidate plate from a list (overhead security camera mode).
     */
    public static <T> MatchResult<T> findBestMatch(
            String detectedPlate,
            List<T> candidates,
            Function<T, String> plateExtractor,
            double threshold) {
        if (detectedPlate == null || detectedPlate.isBlank() || candidates == null || candidates.isEmpty()) {
            return null;
        }
        String det = normalize(detectedPlate);
        T bestItem = null;
        double bestScore = 0.0;
        String bestPlate = null;
        for (T c : candidates) {
            if (c == null) continue;
            String plate = plateExtractor.apply(c);
            if (plate == null || plate.isBlank()) continue;
            double score = similarity(det, plate);
            if (score > bestScore) {
                bestScore = score;
                bestItem = c;
                bestPlate = normalize(plate);
            }
        }
        if (bestItem == null || bestScore < threshold) {
            return null;
        }
        return new MatchResult<>(bestItem, bestPlate, bestScore);
    }

    public static final class MatchResult<T> {
        private final T item;
        private final String matchedPlate;
        private final double score;

        public MatchResult(T item, String matchedPlate, double score) {
            this.item = item;
            this.matchedPlate = matchedPlate;
            this.score = score;
        }

        public T getItem() { return item; }
        public String getMatchedPlate() { return matchedPlate; }
        public double getScore() { return score; }
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }
}

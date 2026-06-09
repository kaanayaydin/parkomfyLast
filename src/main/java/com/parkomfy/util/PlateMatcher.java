package com.parkomfy.util;

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

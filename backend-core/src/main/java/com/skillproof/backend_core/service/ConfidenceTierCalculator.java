package com.skillproof.backend_core.service;

public final class ConfidenceTierCalculator {

    private static final int HIGH_CONFIDENCE_MIN_AVG_LEN = 100;
    private static final int LOW_CONFIDENCE_MAX_AVG_LEN = 40;
    private static final int HIGH_CONFIDENCE_MAX_SCORE_SPREAD = 20;

    private ConfidenceTierCalculator() {
    }

    public static String computeTier(int skipCount,
                                     int avgAnswerLength,
                                     int scoreSpread,
                                     boolean evaluationComplete) {
        if (!evaluationComplete) {
            return "Low";
        }

        if (skipCount == 0
            && avgAnswerLength > HIGH_CONFIDENCE_MIN_AVG_LEN
            && scoreSpread < HIGH_CONFIDENCE_MAX_SCORE_SPREAD) {
            return "High";
        }

        if (skipCount >= 2 || avgAnswerLength < LOW_CONFIDENCE_MAX_AVG_LEN) {
            return "Low";
        }

        if (skipCount == 1 || scoreSpread >= HIGH_CONFIDENCE_MAX_SCORE_SPREAD) {
            return "Medium";
        }

        return "Medium";
    }
}

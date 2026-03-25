package com.skillproof.backend_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConfidenceTierCalculatorTests {

    @Test
    void highConfidenceWhenNoSkipsLongAnswersAndLowSpread() {
        String tier = ConfidenceTierCalculator.computeTier(0, 140, 10, true);
        assertEquals("High", tier);
    }

    @Test
    void mediumConfidenceWhenOneSkip() {
        String tier = ConfidenceTierCalculator.computeTier(1, 120, 10, true);
        assertEquals("Medium", tier);
    }

    @Test
    void mediumConfidenceWhenScoreSpreadHigh() {
        String tier = ConfidenceTierCalculator.computeTier(0, 120, 25, true);
        assertEquals("Medium", tier);
    }

    @Test
    void lowConfidenceWhenTwoSkips() {
        String tier = ConfidenceTierCalculator.computeTier(2, 120, 10, true);
        assertEquals("Low", tier);
    }

    @Test
    void lowConfidenceWhenAnswersVeryShort() {
        String tier = ConfidenceTierCalculator.computeTier(0, 20, 10, true);
        assertEquals("Low", tier);
    }

    @Test
    void lowConfidenceWhenEvaluationIncomplete() {
        String tier = ConfidenceTierCalculator.computeTier(0, 140, 10, false);
        assertEquals("Low", tier);
    }
}

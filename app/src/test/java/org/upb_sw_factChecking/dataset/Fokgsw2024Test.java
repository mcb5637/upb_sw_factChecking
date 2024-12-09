package org.upb_sw_factChecking.dataset;

import org.junit.jupiter.api.Test;

public class Fokgsw2024Test {

    @Test
    void testGetTrainingSet() {
        final var test = Fokgsw2024.getTrainingSet();
        assert !test.getEntries().isEmpty();
    }

    @Test
    void testGetTestSet() {
        final var test = Fokgsw2024.getTestSet();
        assert !test.getEntries().isEmpty();
    }
}

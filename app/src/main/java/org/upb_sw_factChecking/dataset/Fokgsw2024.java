package org.upb_sw_factChecking.dataset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Fokgsw2024 {

    private final static Logger logger = LoggerFactory.getLogger(Fokgsw2024.class);

    public static TrainingSet getTrainingSet() {
        try {
            return new TrainingSet(Fokgsw2024.class.getResourceAsStream("/fokgsw/fokg-sw-train-2024.nt"));
        } catch (IOException e) {
            logger.error("Error reading training set file", e);
            throw new RuntimeException(e);
        }
    }

    public static TestSet getTestSet() {
        try {
            return new TestSet(Fokgsw2024.class.getResourceAsStream("/fokgsw/fokg-sw-test-2024.nt"));
        } catch (IOException e) {
            logger.error("Error reading test set file", e);
            throw new RuntimeException(e);
        }
    }
}

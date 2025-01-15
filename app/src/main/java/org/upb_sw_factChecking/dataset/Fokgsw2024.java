package org.upb_sw_factChecking.dataset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * This class provides access to the FOKGSW 2024 dataset.
 */
public class Fokgsw2024 {

    private final static Logger logger = LoggerFactory.getLogger(Fokgsw2024.class);

    /**
     * Returns the training set of the FOKGSW 2024 dataset.
     *
     * @return the FOKGSW 2024 training set
     */
    public static TrainingSet getTrainingSet() {
        try {
            return new TrainingSet(Fokgsw2024.class.getResourceAsStream("/fokgsw/fokg-sw-train-2024.nt"));
        } catch (IOException e) {
            logger.error("Error reading training set file", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the test set of the FOKGSW 2024 dataset.
     *
     * @return the FOKGSW 2024 test set
     */
    public static TestSet getTestSet() {
        try {
            return new TestSet(Fokgsw2024.class.getResourceAsStream("/fokgsw/fokg-sw-test-2024.nt"));
        } catch (IOException e) {
            logger.error("Error reading test set file", e);
            throw new RuntimeException(e);
        }
    }
}

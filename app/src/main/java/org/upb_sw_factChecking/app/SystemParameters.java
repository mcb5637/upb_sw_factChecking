package org.upb_sw_factChecking.app;

import java.time.Duration;

/**
 * This class provides access to the system parameters.
 */
public class SystemParameters {

    // Used for the calculation of the rule weights.
    public static final double ALPHA = 0.1;
    public static final double BETA = 0.9;
    public static final double GAMMA = 0.25;

    // Local graph generation will start with this path length. If no path has been found,
    // the path length will be increased by one until the absolute maximum path length is reached.
    public static final int INITIAL_MAX_PATH_LENGTH = 3;
    public static final int ABSOLUTE_MAX_PATH_LENGTH = 6;

    // If path search takes less than this and no path has been found, increase path length.
    public static final Duration PATH_TIMEOUT = Duration.ofSeconds(1);
}

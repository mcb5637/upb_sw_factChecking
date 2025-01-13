package org.upb_sw_factChecking.app;

public class SystemParameters {

    public static final double ALPHA = 0.1;
    public static final double BETA = 0.9;
    public static final double GAMMA = 0.25;

    // Local graph generation will start with this path length. If no path has been found,
    // the path length will be increased by one until the absolute maximum path length is reached.
    public static final int INITIAL_MAX_PATH_LENGTH = 3;
    public static final int ABSOLUTE_MAX_PATH_LENGTH = 10;

    // Number of threads to use for rule generation.
    // Something will create additional threads anyway, so shouldn't be set too high.
    public static final int RULE_GEN_THREAD_COUNT = Runtime.getRuntime().availableProcessors() / 4;
}

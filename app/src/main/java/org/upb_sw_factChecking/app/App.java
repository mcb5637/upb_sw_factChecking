package org.upb_sw_factChecking.app;

import org.slf4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Mixin;


@Command(name = "", subcommands = {App.Check.class, App.Evaluate.class}, customSynopsis = "[evaluate | check] [OPTIONS]")
public class App {

    public static final Logger logger = org.slf4j.LoggerFactory.getLogger(App.class);

    static class CommandLineOptions {
        @Option(names = {"-v", "--verbose"}, description = "Be verbose")
        boolean verbose;
    }

    @Command(name = "evaluate", description = "Evaluates the systems performance against a training set.")
    static class Evaluate implements Runnable {
        @Mixin
        CommandLineOptions options;

        @Override
        public void run() {
            logger.info("Evaluating...");
        }
    }

    @Command(name = "check", description = "Checks truthfulness of given facts.")
    static class Check implements Runnable {
        @Override
        public void run() {
            logger.info("Checking...");
        }
    }

    public static void main(String[] args) {
        new CommandLine(new App()).execute(args);
    }

}

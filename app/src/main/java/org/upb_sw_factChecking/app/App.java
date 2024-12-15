package org.upb_sw_factChecking.app;

import org.slf4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.ArgGroup;


@Command(name = "", subcommands = {App.Check.class, App.Evaluate.class}, customSynopsis = "[evaluate | check] [OPTIONS]")
public class App {

    public static final Logger logger = org.slf4j.LoggerFactory.getLogger(App.class);

    static class CommandLineOptions {

        @ArgGroup(exclusive = true, multiplicity = "1", heading = "Test data options%n")
        public TestData testData;

        @ArgGroup(exclusive = true, multiplicity = "1", heading = "Data source options%n")
        public Database database;

        @Option(names = {"-owl", "--owl-file"}, description = "OWL file", required = true)
        public String owlFile;

        @Option(names = {"-o", "--output-file"}, description = "Output file")
        public String outputFile = "result.ttl";

        static class TestData {
            @Option(names = {"fokgsw"}, description = "Use default data from FoKG SW 2024")
            Boolean useDefaultData;
            @Option(names = {"-T", "--test-file"}, description = "Path to test data")
            String test;
        }

        static class Database {
            @Option(names = {"-e", "--endpoint"}, description = "SPARQL endpoint")
            String endpoint;
            @Option(names = {"-d", "--dump-file"}, description = "Dump file")
            String dumpFile;
        }
    }

    @Command(name = "evaluate", description = "Evaluate the systems performance against a training set.")
    static class Evaluate implements Runnable {
        @Mixin
        CommandLineOptions options;

        @Override
        public void run() {
            logger.info("Evaluating...");
        }
    }

    @Command(name = "check", description = "Check truthfulness of given facts.")
    static class Check implements Runnable {
        @Mixin
        CommandLineOptions options;

        @Override
        public void run() {
            logger.info("Checking...");
            logger.info("OWL file: {}", options.owlFile);
            logger.info("Output file: {}", options.outputFile);
            if (options.testData.useDefaultData) {
                logger.info("Using default data");
            } else {
                logger.info("Test file: {}", options.testData.test);
            }
            if (options.database.endpoint != null) {
                logger.info("Endpoint: {}", options.database.endpoint);
            } else {
                logger.info("Dump file: {}", options.database.dumpFile);
            }
        }
    }

    public static void main(String[] args) {
        new CommandLine(new App()).execute(args);
    }

}

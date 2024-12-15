package org.upb_sw_factChecking.app;

import ch.qos.logback.classic.Level;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.upb_sw_factChecking.FactChecker;
import org.upb_sw_factChecking.dataset.Fokgsw2024;
import org.upb_sw_factChecking.dataset.TestSet;
import org.upb_sw_factChecking.dataset.TrainingSet;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.ArgGroup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;


@Command(name = "", subcommands = {App.Check.class, App.Evaluate.class}, customSynopsis = "[evaluate | check] [OPTIONS]")
public class App {

    public static final Logger logger = org.slf4j.LoggerFactory.getLogger(App.class);

    static class CommandLineOptions {

        @ArgGroup(exclusive = true, multiplicity = "1", heading = "Test data options:%n")
        public TestDataOption testData;

        @ArgGroup(exclusive = true, multiplicity = "1", heading = "Data source options%n")
        public DatabaseOption database;

        @Option(names = {"-owl", "--owl-file"}, description = "OWL file", required = true, paramLabel = "<FILE>")
        public String owlFile;

        @Option(names = {"-o", "--output-file"}, description = "Output file", paramLabel = "<FILE>")
        public String outputFile = "result.ttl";

        static class TestDataOption {
            @Option(names = {"fokgsw"}, description = "Use default data from FoKG SW 2024")
            Boolean useDefaultData;
            @Option(names = {"-t", "--test-file"}, description = "Path to test data", paramLabel = "<FILE>")
            String test;
        }

        static class DatabaseOption {
            @Option(names = {"-e", "--endpoint"}, description = "SPARQL endpoint", paramLabel = "<URL>")
            String endpoint;
            @Option(names = {"-d", "--dump-file"}, description = "Dump file", paramLabel = "<FILE>")
            String dumpFile;
        }
    }

    @Command(
            name = "evaluate",
            description = "Evaluate the systems performance against a training set.",
            usageHelpAutoWidth = true,
            separator = " ",
            showDefaultValues = true
    )
    static class Evaluate implements Runnable {
        @Mixin
        CommandLineOptions options;

        @Override
        public void run() {
            // Load the training set
            logger.info("Loading training set.");
            TrainingSet trainingSet;
            if (options.testData.useDefaultData) {
                trainingSet = Fokgsw2024.getTrainingSet();
            } else {
                try {
                    trainingSet = new TrainingSet(Path.of(options.testData.test));
                } catch (IOException e) {
                    logger.error("Error reading training set file", e);
                    throw new RuntimeException(e);
                }
            }

            // Load database + ontology
            logger.info("Loading database and ontology.");
            Model model = RDFDataMgr.loadModel(options.database.dumpFile);
            Model ontology = RDFDataMgr.loadModel(options.owlFile);

            // Evaluate
            logger.info("Inferring rules.");
            final var factChecker = new FactChecker(model.getGraph(), ontology.getGraph());
            logger.info("Evaluating system.");
            logger.info("Checking {} facts.", trainingSet.getEntries().size());
            var averageError = 0.0;
            for (var entry : trainingSet.getEntries()) {
                final double truthValue = factChecker.check(entry.statement());
                final double error = Math.abs(truthValue - entry.truthValue());
                averageError += error;
                logger.info("Truth value for {} is {}, expected was {}, error is {}.", entry.statement(), truthValue, entry.truthValue(), error);
            }
            averageError /= trainingSet.getEntries().size();
            logger.info("Average error: {}", averageError);
        }
    }

    @Command(
            name = "check",
            description = "Check truthfulness of given facts inside the test file.",
            usageHelpAutoWidth = true,
            separator = " ",
            showDefaultValues = true
    )
    static class Check implements Runnable {
        @Mixin
        CommandLineOptions options;

        @Override
        public void run() {
            // Load the training set
            TestSet testSet;
            if (options.testData.useDefaultData) {
                testSet = Fokgsw2024.getTestSet();
            } else {
                try {
                    testSet = new TestSet(Path.of(options.testData.test));
                } catch (IOException e) {
                    logger.error("Error reading test set file", e);
                    throw new RuntimeException(e);
                }
            }

            // Load database + ontology
            logger.info("Loading database and ontology.");
            Model model = RDFDataMgr.loadModel(options.database.dumpFile);
            Model ontology = RDFDataMgr.loadModel(options.owlFile);

            // Evaluate
            logger.info("Inferring rules.");
            final var factChecker = new FactChecker(model.getGraph(), ontology.getGraph());
            final var results = new ArrayList<TrainingSet.TrainingSetEntry>(testSet.getEntries().size());
            for (var entry : testSet.getEntries()) {
                final double truthValue = factChecker.check(entry.statement());
                logger.info("Truth value for {} is {}", entry.statement(), truthValue);
                results.add(entry.toTrainingSetEntry(truthValue));
            }
            try {
                TrainingSet.serializeToResultFile(results, Path.of(options.outputFile));
            } catch (IOException e) {
                logger.error("Error writing result file", e);
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.INFO);
        new CommandLine(new App()).execute(args);
    }

}

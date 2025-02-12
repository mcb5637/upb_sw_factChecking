package org.upb_sw_factChecking.app;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.upb_sw_factChecking.scoring.FactScorer;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


@Command(name = "", subcommands = {App.Check.class, App.Evaluate.class}, customSynopsis = "[evaluate | check] [OPTIONS]")
public class App {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message.")
    boolean helpRequested;

    public static final Logger logger = org.slf4j.LoggerFactory.getLogger(App.class);

    static class CommandLineOptions {
        @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message.")
        boolean helpRequested;

        @ArgGroup(exclusive = true, multiplicity = "1", heading = "Test data options%n")
        public TestDataOption testData;

        @ArgGroup(exclusive = true, multiplicity = "1", heading = "Data source options%n")
        public DatabaseOption database;

        @Option(names = {"-T", "--training-file"}, description = "Path to training data used to generate the rules", paramLabel = "<FILE>")
        public String trainingFile;

        @Option(names = {"-r", "--rules-file"}, description = "Path to file where rules will be loaded from or saved after generation", paramLabel = "<FILE>", defaultValue = "rules.txt")
        public String rulesFile;

        static class TestDataOption {
            @Option(names = {"fokgsw"}, description = "Use default data from FoKG SW 2024")
            Boolean useDefaultData = false;
            @Option(names = {"-t", "--test-file"}, description = "Path to test data", paramLabel = "<FILE>")
            String test;
        }

        static class DatabaseOption {
            //@Option(names = {"-e", "--endpoint"}, description = "SPARQL endpoint", paramLabel = "<URL>")
            //String endpoint;
            @Option(names = {"-d", "--dump-file"}, description = "Dump file", paramLabel = "<FILE>")
            String dumpFile;
        }

        @Option(names = {"--labels"}, description = "Display labels instead of URIs", defaultValue = "false")
        boolean displayLabels = false;
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
            TrainingSet trainingSet = loadTrainingSet(options.trainingFile, options.testData.useDefaultData);

            // Load database
            logger.info("Loading database.");
            Model model = RDFDataMgr.loadModel(options.database.dumpFile);

            // Load rules
            final var factChecker = loadFactScorer(model, trainingSet, options.rulesFile);

            logger.info("Evaluating system.");
            logger.info("Checking {} facts.", trainingSet.getEntries().size());
            AtomicReference<Double> averageError = new AtomicReference<>(0.0);
            AtomicInteger count = new AtomicInteger();
            trainingSet.getEntries().parallelStream().forEach(entry -> {
                final double truthValue = factChecker.scoreStatement(entry.statement());
                synchronized (factChecker) {
                    final double error = Math.abs(truthValue - entry.truthValue());
                    averageError.updateAndGet(v -> v + error);
                    logger.info("Truth value for '{}' is {}, expected was {}, error is {}.",
                            options.displayLabels ? labeledStatement(model, entry.statement()) : entry.statement(),
                            truthValue, entry.truthValue(), error);
                    logger.info("{} facts remaining.", trainingSet.getEntries().size() - count.incrementAndGet());
                }
            });

            averageError.updateAndGet(v -> v / trainingSet.getEntries().size());
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

        @Option(names = {"-o", "--output-file"}, description = "Output file", paramLabel = "<FILE>", defaultValue = "result.ttl")
        public String outputFile = "result.ttl";

        @Override
        public void run() {
            // Load the test set
            TestSet testSet = loadTestSet(options.testData.test, options.testData.useDefaultData);

            // Load the training set
            TrainingSet trainingSet = loadTrainingSet(options.trainingFile, options.testData.useDefaultData);

            // Load database
            logger.info("Loading database.");
            Model model = RDFDataMgr.loadModel(options.database.dumpFile);

            // Load rules
            final var factChecker = loadFactScorer(model, trainingSet, options.rulesFile);

            final var results = new ArrayList<TrainingSet.TrainingSetEntry>(testSet.getEntries().size());
            AtomicInteger count = new AtomicInteger();
            testSet.getEntries().parallelStream().forEach(entry -> {
                final double truthValue = factChecker.scoreStatement(entry.statement());

                synchronized (factChecker) {
                    logger.info("Truth value for '{}' is {}",
                            options.displayLabels ? labeledStatement(model, entry.statement()) : entry.statement(),
                            truthValue);
                    logger.info("{} facts remaining.", testSet.getEntries().size() - count.incrementAndGet());
                    results.add(entry.toTrainingSetEntry(truthValue));
                }
            });
            try {
                TrainingSet.serializeToResultFile(results, Path.of(outputFile));
            } catch (IOException e) {
                logger.error("Error writing result file", e);
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) {
        new CommandLine(new App()).execute(args);
    }

    /**
     * Returns a string representation of a statement, in which the URI's of the entities are replaced with their labels.
     *
     * @param m         The model containing the labels
     * @param statement The statement to be labeled
     * @return          A string representation of the statement
     */
    private static String labeledStatement(Model m, Statement statement) {
        AtomicReference<String> subjectLabel = new AtomicReference<>(statement.getSubject().getURI());
        AtomicReference<String> predicateLabel = new AtomicReference<>(statement.getPredicate().getURI());
        AtomicReference<String> objectLabel = new AtomicReference<>(statement.getObject().isResource() ? statement.getObject().asResource().getURI() : statement.getObject().asLiteral().getString());

        m.listObjectsOfProperty(statement.getSubject(), RDFS.label).forEachRemaining(o -> subjectLabel.set(o.asLiteral().getString()));
        m.listObjectsOfProperty(statement.getPredicate(), RDFS.label).forEachRemaining(o -> predicateLabel.set(o.asLiteral().getString()));
        m.listObjectsOfProperty(statement.getObject().isResource() ? statement.getObject().asResource() : statement.getPredicate(), RDFS.label).forEachRemaining(o -> objectLabel.set(o.asLiteral().getString()));

        return String.format("%s %s %s", subjectLabel, predicateLabel, objectLabel);
    }

    /**
     * Load the training set from the given path or use the default data.
     *
     * @param path           The path to the training set file
     * @param useDefaultData Whether to use the default data
     * @return               The training set
     */
    public static TrainingSet loadTrainingSet(String path, boolean useDefaultData) {
        logger.info("Loading training set.");
        TrainingSet trainingSet;
        if (useDefaultData) {
            trainingSet = Fokgsw2024.getTrainingSet();
        } else {
            try {
                trainingSet = new TrainingSet(Path.of(path));
            } catch (IOException e) {
                logger.error("Error reading training set file", e);
                throw new RuntimeException(e);
            }
        }
        return trainingSet;
    }

    /**
     * Load the test set from the given path or use the default data.
     *
     * @param path           The path to the test set file
     * @param useDefaultData Whether to use the default data
     * @return               The test set
     */
    public static TestSet loadTestSet(String path, boolean useDefaultData) {
        logger.info("Loading test set.");
        TestSet testSet;
        if (useDefaultData) {
            testSet = Fokgsw2024.getTestSet();
        } else {
            try {
                testSet = new TestSet(Path.of(path));
            } catch (IOException e) {
                logger.error("Error reading test set file", e);
                throw new RuntimeException(e);
            }
        }
        return testSet;
    }

    /**
     * Load the fact scorer from the given rules file or generate the rules if the file does not exist.
     *
     * @param model       The model containing the data
     * @param trainingSet The training set
     * @param rulesFile   The path to the rules file
     * @return            The fact scorer
     */
    public static FactScorer loadFactScorer(Model model, TrainingSet trainingSet, String rulesFile) {
        final var factChecker = new FactScorer(model);
        if (factChecker.loadRulesFromFile(Path.of(rulesFile))) {
            logger.info("Loaded existing rules from file.");
        } else {
            logger.info("Inferring rules.");
            factChecker.generateAndWeightRules(trainingSet, SystemParameters.ALPHA, SystemParameters.BETA, SystemParameters.GAMMA);

            // Save rules
            try {
                factChecker.saveRulesToFile(Path.of(rulesFile));
            } catch (IOException e) {
                logger.error("Error writing rules file", e);
                throw new RuntimeException(e);
            }
        }
        return factChecker;
    }

}

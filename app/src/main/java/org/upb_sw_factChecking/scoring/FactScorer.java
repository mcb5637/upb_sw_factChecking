package org.upb_sw_factChecking.scoring;

import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.upb_sw_factChecking.app.SystemParameters;
import org.upb_sw_factChecking.dataset.TrainingSet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class FactScorer {

    private final Model knownFacts;
    private WeightedRule[] positiveRules;
    private WeightedRule[] negativeRules;

    private final static int INITIAL_MAX_PATH_LENGTH = SystemParameters.INITIAL_MAX_PATH_LENGTH;

    // Number of threads to use for rule generation.
    // Something will create additional threads anyway, so shouldn't be set too high.
    private final static int THREAD_COUNT = SystemParameters.RULE_GEN_THREAD_COUNT;

    private final static Logger logger = org.slf4j.LoggerFactory.getLogger(FactScorer.class);

    public FactScorer(Model knownFacts) {
        this.knownFacts = knownFacts;
    }

    public void generateAndWeightRules(TrainingSet trainingSet, double alpha, double beta, double gamma) {
        Map<Rule, Set<TrainingSet.TrainingSetEntry>> coverage = Collections.synchronizedMap(new HashMap<>());
        Map<String, Set<TrainingSet.TrainingSetEntry>> coverageUnbound = Collections.synchronizedMap(new HashMap<>()); // TODO:
        Set<WeightedRule> ruleSet = Collections.synchronizedSet(new HashSet<>());

        try (ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT)) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            AtomicInteger counter = new AtomicInteger();
            // Collect all rules and assign the covered examples to them.
            for (var example : trainingSet.getEntries()) {
                final var future = CompletableFuture.runAsync(() -> {
                    final var ruleArray = WeightedRule.generateRules(knownFacts, example.statement(), example.truthValue() == 1.0, INITIAL_MAX_PATH_LENGTH);
                    logger.info("Example Number {} of {}: Generated {} rules for example {}.", counter.incrementAndGet(), trainingSet.getEntries().size(), ruleArray.length, example.statement());
                    for (var rule : ruleArray) {
                        final var exampleList = coverage.computeIfAbsent(rule.rule, r -> Collections.synchronizedSet(new HashSet<>()));
                        final var exampleListUnbound = coverageUnbound.computeIfAbsent(rule.rule.getHead()[0].toString().split(" ")[1], r -> Collections.synchronizedSet(new HashSet<>()));
                        exampleList.add(example);
                        exampleListUnbound.add(example);
                        ruleSet.add(rule);
                    }
                }, executorService);
                futures.add(future);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executorService.shutdown();
        }

        // Counter covered examples for each rule.
        for (WeightedRule rule : ruleSet) {
            final var examples = coverage.get(rule.rule);
            AtomicInteger countPositive = new AtomicInteger();
            AtomicInteger countCounter = new AtomicInteger();

            examples.forEach(example -> {
                if ((example.truthValue() == 1.0 && rule.isPositive) || (example.truthValue() == 0.0 && !rule.isPositive)) {
                    countPositive.getAndIncrement();
                } else {
                    countCounter.getAndIncrement();
                }
            });

            final var examplesUnbound = coverageUnbound.get(rule.rule.getHead()[0].toString().split(" ")[1]);
            AtomicInteger unboundCountPositive = new AtomicInteger();
            AtomicInteger unboundCountCounter = new AtomicInteger();

            examplesUnbound.forEach(example -> {
                if ((example.truthValue() == 1.0 && rule.isPositive) || (example.truthValue() == 0.0 && !rule.isPositive)) {
                    unboundCountPositive.getAndIncrement();
                } else {
                    unboundCountCounter.getAndIncrement();
                }
            });

            rule.setCounters(countPositive.get(), countCounter.get(), unboundCountPositive.get(), unboundCountCounter.get());
        }

        // Calculate and assign weights.
        for (var rule : ruleSet) {
            double temp1 = rule.getNumberOfCoveredExamples() == 0 ? 0 : (double) rule.getNumberOfCoveredExamples() / rule.getNumberOfCoveredExamplesUnbound();
            double temp2 = rule.getNumberOfCoveredCounters() == 0 ? 0 : (double) rule.getNumberOfCoveredCounters() / rule.getNumberOfCoveredCountersUnbound();
            double w2r = alpha * (1 - temp1) + beta * temp2;
            double wcr = 1 -
                    ((rule.getNumberOfCoveredExamples() - 1 / gamma * rule.getNumberOfCoveredCounters()) /
                        rule.getNumberOfCoveredExamples()) * (1 - w2r);
            // if there are many more counters than examples, the weight might be above 1.0, which is not allowed.
            rule.setWeight(Double.min(1.0, wcr));
        }

        // Create sorted rule arrays.
        positiveRules = ruleSet.stream().sorted(Comparator.comparingDouble(rule -> rule.weight)).filter(weightedRule -> weightedRule.isPositive).toArray(WeightedRule[]::new);
        negativeRules = ruleSet.stream().sorted(Comparator.comparingDouble(rule -> rule.weight)).filter(weightedRule -> !weightedRule.isPositive).toArray(WeightedRule[]::new);
    }

    public double scoreStatement(Statement fact) {
        AtomicReference<Double> minPositiveW  = new AtomicReference<>(1.0);
        AtomicReference<Rule> positiveRule = new AtomicReference<>();
        Arrays.stream(positiveRules).forEachOrdered(rule -> {
            if (rule.doesRuleApply(knownFacts, fact) && minPositiveW.get() == 1.0) {
                if (minPositiveW.getAndUpdate(d -> rule.weight < d ? rule.weight : d) > rule.weight) {
                    positiveRule.set(rule.rule);
                }
            }
        });

        AtomicReference<Double> minNegativeW = new AtomicReference<>(1.0); // TODO: idk, seems to make more sense tbh
        AtomicReference<Rule> negativeRule = new AtomicReference<>();
        if (minPositiveW.get() == 1.0) {
            Arrays.stream(negativeRules).forEachOrdered(rule -> {
                if (rule.doesRuleApply(knownFacts, fact) && minNegativeW.get() == 1.0) {
                    if (minNegativeW.getAndUpdate(d -> rule.weight < d ? rule.weight : d) > rule.weight) {
                        negativeRule.set(rule.rule);
                    }
                }
            });
        }

        // logger.info("Positive rule: {}", positiveRule.get());
        // logger.info("Negative rule: {}", negativeRule.get());

        synchronized(this) {
            if (positiveRule.get() != null) {
                logger.info("Positive evidence path: {}", instantiateRule(positiveRule.get(), fact, true));
                logger.info("Positive evidence path: {}", instantiateRule(positiveRule.get(), fact, false));
            } else if (negativeRule.get() != null) {
                logger.info("Negative evidence path: {}", instantiateRule(negativeRule.get(), fact, true));
                logger.info("Negative evidence path: {}", instantiateRule(negativeRule.get(), fact, false));
            } else {
                logger.warn("No evidence path found for {}", fact);
            }
            return (((1 - minPositiveW.get()) - (1 - minNegativeW.get())) + 1) / 2;
        }

    }

    public void saveRulesToFile(Path file) throws IOException {
        final var combinedArray = new WeightedRule[positiveRules.length + negativeRules.length];
        System.arraycopy(positiveRules, 0, combinedArray, 0, positiveRules.length);
        System.arraycopy(negativeRules, 0, combinedArray, positiveRules.length, negativeRules.length);
        WeightedRule.serializeRules(combinedArray, file);
    }

    public boolean loadRulesFromFile(Path file) {
        if (!file.toFile().exists()) {
            return false;
        }

        final WeightedRule[] rules;
        try {
            rules = WeightedRule.loadRules(file);
            if (rules.length == 0) {
                logger.warn("No rules loaded from file.");
                return false;
            }
        } catch (IOException e) {
            logger.error("Error reading rules file", e);
            return false;
        }

        Arrays.sort(rules, Comparator.comparingDouble(rule -> rule.weight));
        positiveRules = Arrays.stream(rules).filter(weightedRule -> weightedRule.isPositive).toArray(WeightedRule[]::new);
        negativeRules = Arrays.stream(rules).filter(weightedRule -> !weightedRule.isPositive).toArray(WeightedRule[]::new);
        return true;
    }

    private String instantiateRule(Rule rule, Statement fact, boolean labeled) {
        SelectBuilder builder = new SelectBuilder();
        builder.addVar("*");
        if (rule.bodyLength() < 2) {
            return rule.toShortString();
        }

        builder.addWhere(fact.getSubject(), ResourceFactory.createProperty(rule.getBody()[0].toString().split(" ")[1].replace("@", "http://rdf.freebase.com/ns/")), "?e1");
        for (int i = 1; i < rule.bodyLength() - 1; i++) {
            builder.addWhere("?e" + i, ResourceFactory.createProperty(rule.getBody()[i].toString().split(" ")[1].replace("@", "http://rdf.freebase.com/ns/")), "?e" + (i + 1));
        }
        builder.addWhere("?e" + (rule.bodyLength() - 1), ResourceFactory.createProperty(rule.getBody()[rule.bodyLength() - 1].toString().split(" ")[1].replace("@", "http://rdf.freebase.com/ns/")), fact.getObject().isResource() ? fact.getObject().asResource() : fact.getObject().asLiteral());

        var ruleString = rule.toShortString();
        try (var qexec = QueryExecutionFactory.create(builder.build(), knownFacts)) {
            var result = qexec.execSelect().nextSolution();
            for (int i = 1; i < rule.bodyLength(); i++) {
                var resourceString = result.getResource("?e" + i).toString();
                if (labeled) {
                    final var label = knownFacts.listObjectsOfProperty(result.getResource("?e" + i), RDFS.label).nextOptional();
                    if (label.isPresent())
                        resourceString = label.get().asLiteral().getLexicalForm();
                }
                ruleString = ruleString.replaceAll("\\?e" + i, resourceString);
            }
            var subjectString = fact.getSubject().toString();
            var objectString = fact.getObject().toString(); // assuming the object is always uri
            if (labeled) {
                final var subjectLabel = knownFacts.listObjectsOfProperty(fact.getSubject(), RDFS.label).nextOptional();
                final var objectLabel = knownFacts.listObjectsOfProperty(fact.getObject().asResource(), RDFS.label).nextOptional();
                if (subjectLabel.isPresent())
                    subjectString = subjectLabel.get().asLiteral().getLexicalForm();
                if (objectLabel.isPresent())
                    objectString = objectLabel.get().asLiteral().getLexicalForm();
            }
            ruleString = ruleString.replaceAll("\\?e0", subjectString).replaceAll("\\?e" + rule.bodyLength(), objectString);
        }
        return ruleString;
    }
}

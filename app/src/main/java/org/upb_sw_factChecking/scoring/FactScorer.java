package org.upb_sw_factChecking.scoring;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.rulesys.Rule;
import org.slf4j.Logger;
import org.upb_sw_factChecking.dataset.TrainingSet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class FactScorer {

    private final Model knownFacts;
    private WeightedRule[] positiveRules;
    private WeightedRule[] negativeRules;

    private final static int MAX_PATH_LENGTH = 3;
    private final static int THREAD_COUNT = 4;

    private final static Logger logger = org.slf4j.LoggerFactory.getLogger(FactScorer.class);

    public FactScorer(Model knownFacts) {
        this.knownFacts = knownFacts;
    }

    public void generateAndWeightRules(TrainingSet trainingSet, double alpha, double beta, double gamma) {
        Map<Rule, Set<TrainingSet.TrainingSetEntry>> coverage = Collections.synchronizedMap(new HashMap<>());
        Map<Rule, Set<TrainingSet.TrainingSetEntry>> coverageUnbound = Collections.synchronizedMap(new HashMap<>());
        Set<WeightedRule> ruleSet = Collections.synchronizedSet(new HashSet<>());

        try (ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT)) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            AtomicInteger counter = new AtomicInteger();
            // Collect all rules and assign the covered examples to them.
            for (var example : trainingSet.getEntries()) {
                final var future = CompletableFuture.runAsync(() -> {
                    final var ruleArray = WeightedRule.generateRules(knownFacts, example.statement(), example.truthValue() == 1.0, MAX_PATH_LENGTH);
                    logger.info("Example Number {} of {}: Generated {} rules for example {}.", counter.incrementAndGet(), trainingSet.getEntries().size(), ruleArray.length, example.statement());
                    for (var rule : ruleArray) {
                        final var exampleList = coverage.computeIfAbsent(rule.rule, r -> Collections.synchronizedSet(new HashSet<>()));
                        final var exampleListUnbound = coverageUnbound.computeIfAbsent(rule.unboundRule, r -> Collections.synchronizedSet(new HashSet<>()));
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

            final var examplesUnbound = coverageUnbound.get(rule.unboundRule);
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

    public double check(Statement fact) { return 0.0 ; }

    public void saveRulesToFile(Path file) throws IOException {
        final var combinedArray = new WeightedRule[positiveRules.length + negativeRules.length];
        System.arraycopy(positiveRules, 0, combinedArray, 0, positiveRules.length);
        System.arraycopy(negativeRules, 0, combinedArray, positiveRules.length, negativeRules.length);
        WeightedRule.serializeRules(combinedArray, file);
    }

    public void loadRulesFromFile(Path file) throws IOException {
        final var rules = WeightedRule.loadRules(file);
        Arrays.sort(rules, Comparator.comparingDouble(rule -> rule.weight));
        positiveRules = Arrays.stream(rules).filter(weightedRule -> weightedRule.isPositive).toArray(WeightedRule[]::new);
        negativeRules = Arrays.stream(rules).filter(weightedRule -> !weightedRule.isPositive).toArray(WeightedRule[]::new);
    }
}

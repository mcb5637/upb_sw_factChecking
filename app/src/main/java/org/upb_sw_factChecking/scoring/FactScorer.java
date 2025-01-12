package org.upb_sw_factChecking.scoring;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.rulesys.Rule;
import org.upb_sw_factChecking.dataset.TrainingSet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FactScorer {

    private final Model knownFacts;
    private WeightedRule[] positiveRules;
    private WeightedRule[] negativeRules;

    public FactScorer(Model knownFacts) {
        this.knownFacts = knownFacts;
    }

    public void generateAndWeightRules(TrainingSet trainingSet, double alpha, double beta, double gamma) {
        HashMap<Rule, Set<TrainingSet.TrainingSetEntry>> coverage = new HashMap<>();
        HashMap<Rule, Set<TrainingSet.TrainingSetEntry>> coverageUnbound = new HashMap<>();
        List<WeightedRule> ruleList = new ArrayList<>();

        // Collect all rules and assign the covered examples to them.
        for (var example : trainingSet.getEntries()) {
            final var ruleArray = WeightedRule.generateRules(knownFacts, example.statement(), example.truthValue() == 1.0, 10);
            for (var rule : ruleArray) {
                final var exampleList = coverage.computeIfAbsent(rule.rule, r -> new HashSet<>());
                final var exampleListUnbound = coverageUnbound.computeIfAbsent(rule.unboundRule, r -> new HashSet<>());
                exampleList.add(example);
                exampleListUnbound.add(example);
                ruleList.add(rule);
            }
        }

        // Counter covered examples for each rule.
        for (WeightedRule rule : ruleList) {
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

            final var examplesUnbound = coverageUnbound.get(rule.rule);
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
        for (var rule : ruleList) {
            double w2r =
                    alpha * (1 - ((double) rule.getNumberOfCoveredExamples() / rule.getNumberOfCoveredExamplesUnbound())) +
                    beta * ((double) rule.getNumberOfCoveredCounters() / rule.getNumberOfCoveredCountersUnbound());
            double wcr = 1 -
                    (((rule.getNumberOfCoveredExamples() - 1) / gamma * rule.getNumberOfCoveredCounters()) /
                        rule.getNumberOfCoveredExamples()) * (1 - w2r);
            rule.setWeight(wcr);
        }

        // Create sorted rule arrays.
        ruleList.sort(Comparator.comparingDouble(rule -> rule.weight));
        positiveRules = ruleList.stream().filter(weightedRule -> weightedRule.isPositive).toArray(WeightedRule[]::new);
        negativeRules = ruleList.stream().filter(weightedRule -> !weightedRule.isPositive).toArray(WeightedRule[]::new);
    }

    public double check(Statement fact) { return 0.0 ; }

    public void saveRulesToFile(Path file) throws IOException {
        final var combinedArray = new WeightedRule[positiveRules.length + negativeRules.length];
        System.arraycopy(positiveRules, 0, combinedArray, 0, positiveRules.length);
        System.arraycopy(negativeRules, 0, combinedArray, positiveRules.length, negativeRules.length);
        WeightedRule.serializeRules(combinedArray, file);
    }

    public void loadRulesFromFile(Path file) throws IOException {
        final var rules = WeightedRule.loadRules(file, knownFacts);
        Arrays.sort(rules, Comparator.comparingDouble(rule -> rule.weight));
        positiveRules = Arrays.stream(rules).filter(weightedRule -> weightedRule.isPositive).toArray(WeightedRule[]::new);
        negativeRules = Arrays.stream(rules).filter(weightedRule -> !weightedRule.isPositive).toArray(WeightedRule[]::new);
    }
}

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


/**
 * This class scores facts based on the rules generated from the training set.
 */
public class FactScorer {

    private final Model knownFacts;
    private WeightedRule[] positiveRules;
    private WeightedRule[] negativeRules;

    private final static int INITIAL_MAX_PATH_LENGTH = SystemParameters.INITIAL_MAX_PATH_LENGTH;

    private final static Logger logger = org.slf4j.LoggerFactory.getLogger(FactScorer.class);

    public FactScorer(Model knownFacts) {
        this.knownFacts = knownFacts;
    }

    /**
     * Generates and weights rules based on the training set.
     *
     * @param trainingSet the training set to generate rules from
     * @param alpha       the alpha parameter for the rule weight calculation
     * @param beta        the beta parameter for the rule weight calculation
     * @param gamma       the gamma parameter for the rule weight calculation
     */
    public void generateAndWeightRules(TrainingSet trainingSet, double alpha, double beta, double gamma) {
        Map<Rule, Set<TrainingSet.TrainingSetEntry>> coverage = Collections.synchronizedMap(new HashMap<>());               // Rule -> Set of covered examples
        Map<String, Set<TrainingSet.TrainingSetEntry>> coveragePredicates = Collections.synchronizedMap(new HashMap<>());  // Predicate -> Set of covered examples
        Set<WeightedRule> ruleSet = Collections.synchronizedSet(new HashSet<>());                                           // Set of generated rules

        // Generate rules for each example in the training set.
        AtomicInteger counter = new AtomicInteger();
        trainingSet.getEntries().parallelStream().forEach(example -> {
            final var ruleArray = WeightedRule.generateRules(knownFacts, example.statement(), example.truthValue() == 1.0, INITIAL_MAX_PATH_LENGTH);
            logger.info("Example Number {} of {}: Generated {} rules for example {}.", counter.incrementAndGet(), trainingSet.getEntries().size(), ruleArray.length, example.statement());

            // For each generated rule, add it to the rule set and update the coverage maps.
            for (var rule : ruleArray) {
                final var coveredExampleList = coverage.computeIfAbsent(rule.rule, r -> Collections.synchronizedSet(new HashSet<>()));
                final var predicateCoveredExamplesPredicateList = coveragePredicates.computeIfAbsent(getPredicate(rule.rule), r -> Collections.synchronizedSet(new HashSet<>()));
                coveredExampleList.add(example);
                predicateCoveredExamplesPredicateList.add(example);
                ruleSet.add(rule);
            }
        });

        // Counter covered examples for each rule.
        for (WeightedRule rule : ruleSet) {
            // Count covered examples by rule.
            final var examples = coverage.get(rule.rule);
            AtomicInteger countCorrect = new AtomicInteger();
            AtomicInteger countCounter = new AtomicInteger();

            examples.forEach(example -> {
                // If rule is positive and example is true or rule is negative and example is false,
                // increment correct counter.
                if ((example.truthValue() == 1.0 && rule.isPositive) || (example.truthValue() == 0.0 && !rule.isPositive)) {
                    countCorrect.getAndIncrement();
                } else {
                    countCounter.getAndIncrement();
                }
            });

            // Count covered examples by predicate.
            final var examplesPredicate = coveragePredicates.get(getPredicate(rule.rule));
            AtomicInteger predicateCountCorrect = new AtomicInteger();
            AtomicInteger predicateCountCounter = new AtomicInteger();
            examplesPredicate.forEach(example -> {
                if ((example.truthValue() == 1.0 && rule.isPositive) || (example.truthValue() == 0.0 && !rule.isPositive)) {
                    predicateCountCorrect.getAndIncrement();
                } else {
                    predicateCountCounter.getAndIncrement();
                }
            });

            // Assign counters to rule for later weight calculation.
            rule.setCounters(countCorrect.get(), countCounter.get(), predicateCountCorrect.get(), predicateCountCounter.get());
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
        AtomicReference<Double> minPositiveWeight  = new AtomicReference<>(1.0); // initialize with 1.0
        AtomicReference<Rule> positiveRule = new AtomicReference<>();
        for (WeightedRule rule : positiveRules) {
            if (rule.doesRuleApply(knownFacts, fact)) {
                minPositiveWeight.getAndSet(rule.weight);
                positiveRule.set(rule.rule);
                break;
            }
        }

        AtomicReference<Double> minNegativeWeight = new AtomicReference<>(1.0); // initialize with 1.0
        AtomicReference<Rule> negativeRule = new AtomicReference<>();
        // if we already found a positive rule that applies, we don't need to check the negative rules
        if (minPositiveWeight.get() == 1.0) {
            for (WeightedRule rule : negativeRules) {
                if (rule.doesRuleApply(knownFacts, fact)) {
                    minNegativeWeight.getAndSet(rule.weight);
                    negativeRule.set(rule.rule);
                    break;
                }
            }
        }

        // Synchronization just to prevent interleaving of the log messages.
        synchronized(this) {
            if (positiveRule.get() != null) {
                logger.info("Positive evidence path: {}", instantiateRule(knownFacts, positiveRule.get(), fact, true));
                logger.info("Positive evidence path: {}", instantiateRule(knownFacts, positiveRule.get(), fact, false));
            } else if (negativeRule.get() != null) {
                logger.info("Negative evidence path: {}", instantiateRule(knownFacts, negativeRule.get(), fact, true));
                logger.info("Negative evidence path: {}", instantiateRule(knownFacts, negativeRule.get(), fact, false));
            } else {
                logger.warn("No evidence path found for {}", fact);
            }

            // Score the fact based on the weights of the rules.
            return (((1 - minPositiveWeight.get()) - (1 - minNegativeWeight.get())) + 1) / 2;
        }

    }

    /**
     * Saves the rules to a file.
     *
     * @param file         the file to save the rules to
     * @throws IOException if an error occurs while writing the file
     */
    public void saveRulesToFile(Path file) throws IOException {
        final var combinedArray = new WeightedRule[positiveRules.length + negativeRules.length];
        System.arraycopy(positiveRules, 0, combinedArray, 0, positiveRules.length);
        System.arraycopy(negativeRules, 0, combinedArray, positiveRules.length, negativeRules.length);
        WeightedRule.serializeRules(combinedArray, file);
    }

    /**
     * Loads the rules from a file.
     *
     * @param file the file to load the rules from
     * @return     true if the rules were loaded successfully, false otherwise
     */
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


    /**
     * Creates a string representation of the evidence path for a fact.
     * The evidence path is instantiated with the labels of the resources if labeled is true.
     * Does this by executing a SPARQL query on the known facts.
     *
     * @param knownFacts base knowledge graph
     * @param rule       the rule to instantiate
     * @param fact       the fact to instantiate the rule with
     * @param labeled    whether to use labels for the resources
     * @return           the string representation of the evidence path
     */
    private static String instantiateRule(Model knownFacts, Rule rule, Statement fact, boolean labeled) {
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
            final var execution = qexec.execSelect();
            if (!execution.hasNext())
                return ruleString;
            final var result = execution.next();
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

    /**
     * Returns the predicate of the head from a rule.
     *
     * @param rule the rule to get the predicate from
     * @return     the predicate of the head
     */
    private static String getPredicate(Rule rule) {
        return rule.getHead()[0].toString().split(" ")[1];
    }
}

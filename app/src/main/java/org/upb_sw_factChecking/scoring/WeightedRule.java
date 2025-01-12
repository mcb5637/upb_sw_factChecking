package org.upb_sw_factChecking.scoring;

import org.apache.jena.arq.querybuilder.ConstructBuilder;
import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.InfGraph;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Class containing the rule and additional information.
 */
public class WeightedRule {

    public Rule rule;
    public Rule unboundRule;
    public Reasoner reasoner;
    public boolean isPositive;
    public double weight;
    public InfGraph inferred;

    private int numberOfCoveredExamples;
    private int numberOfCoveredExamplesUnbound;
    private int numberOfCoveredCounters;
    private int numberOfCoveredCountersUnbound;

    private final static int ABSOLUTE_MAX_PATH_LENGTH = 20;

    private static final Logger logger = LoggerFactory.getLogger(WeightedRule.class);

    public WeightedRule(Rule rule, Rule unboundRule, boolean isPositive) {
        this.rule = rule;
        this.unboundRule = unboundRule;
        this.isPositive = isPositive;
        this.weight = 0.0;              // default value
        // this.reasoner = new GenericRuleReasoner(List.of(rule));
    }

    public static WeightedRule[] generateRules(Model baseModel, Statement example, boolean isPositive, int maxPathLength) {
        Model localGraph = createLocalGraph(baseModel, example.getSubject(), example.getObject(), maxPathLength);
        Statement[][] paths = createPaths(localGraph, example.getSubject(), example.getObject(), ABSOLUTE_MAX_PATH_LENGTH);
        Rule[] rules = createRules(paths, example);
        Rule[] unboundRules = createUnboundRules(paths, example);

        WeightedRule[] result = new WeightedRule[rules.length];
        for (int i = 0; i < rules.length; i++) {
            result[i] = new WeightedRule(rules[i], unboundRules[i], isPositive);
            // result[i].inferred = result[i].reasoner.bind(baseModel.getGraph());
        }

        return result;
    }

    public static Model createLocalGraph(Model baseModel, Resource subject, RDFNode object, int maxPathLength) {
        Model localGraph = ModelFactory.createDefaultModel();
        boolean foundSomething = false;

        // For each pathLength less than maxPathLength, create and execute a CONSTRUCT query, that generates a graph
        // that contains every path from the given subject to the given object with that given length.
        for (int currentPathLength = 1; (currentPathLength <= maxPathLength || !foundSomething) && (currentPathLength <= ABSOLUTE_MAX_PATH_LENGTH); currentPathLength++) {

            // Build the CONSTRUCT query.
            ConstructBuilder builder = new ConstructBuilder();
            if (currentPathLength == 1) {
                // If the path length is 1, the pattern differs a bit.
                builder.addConstruct(subject, "?p", object);
                builder.addWhere(subject, "?p", object);
            } else {
                builder.addConstruct(subject, "?p0", "?e1");
                builder.addWhere(subject, "?p0", "?e1");
                for (int i = 1; i < currentPathLength - 1; i++) {
                    builder.addConstruct("?e" + i, "?p" + i, "?e" + (i + 1));
                    builder.addWhere("?e" + i, "?p" + i, "?e" + (i + 1));
                }
                builder.addConstruct("?e" + (currentPathLength - 1), "?p" + (currentPathLength - 1), object);
                builder.addWhere("?e" + (currentPathLength - 1), "?p" + (currentPathLength - 1), object);
            }

            // Execute the CONSTRUCT query and add the result to the local graph.
            try (QueryExecution qexec = QueryExecutionFactory.create(builder.build(), baseModel)) {
                final var temp = qexec.execConstruct();
                foundSomething = !temp.isEmpty();
                if (currentPathLength >= maxPathLength - 1 && !foundSomething) {
                    logger.info("Path length of {} reached, but no path found. Extending to {}.", maxPathLength, currentPathLength + 1);
                }
                localGraph.add(temp);
            }
        }

        // TODO: add unequal predicates

        return localGraph;
    }

    public static Statement[][] createPaths(Model localGraph, Resource subject, RDFNode object, int maxPathLength) {
        List<Statement[]> paths = new ArrayList<>();

        for (int currentPathLength = 1; currentPathLength <= maxPathLength; currentPathLength++) {

            // Build SELECT query.
            SelectBuilder selectBuilder = new SelectBuilder();
            if (currentPathLength == 1) {
                selectBuilder.addVar("?p0").addWhere(subject, "?p0", object);
            } else {
                selectBuilder.addVar("?p0").addVar("?e1");
                selectBuilder.addWhere(subject, "?p0", "?e1");
                for (int i = 1; i < currentPathLength - 1; i++) {
                    selectBuilder.addVar("?p" + i).addVar("?e" + (i + 1));
                    selectBuilder.addWhere("?e" + i, "?p" + i, "?e" + (i + 1));
                }
                selectBuilder.addVar("?p" + (currentPathLength - 1));
                selectBuilder.addWhere("?e" + (currentPathLength - 1), "?p" + (currentPathLength - 1), object);
            }
            
            // Execute SELECT query.
            try (QueryExecution qexec = QueryExecutionFactory.create(selectBuilder.build(), localGraph)) {
                final var results = qexec.execSelect();
                while (results.hasNext()) {
                    final var next = results.nextSolution();
                    Statement[] path = new Statement[currentPathLength];
                    if (currentPathLength == 1) {
                        final var property = ResourceFactory.createProperty(next.getResource("?p0").getURI());
                        path[0] = ResourceFactory.createStatement(subject, property, object);
                        paths.add(path);
                    } else {
                        var currentSubject = subject;
                        for (int i = 0; i < currentPathLength - 1; i++) {
                            final var property = ResourceFactory.createProperty(next.getResource("?p" + i).getURI());
                            final var currentObject = next.getResource("?e" + (i + 1));
                            path[i] = ResourceFactory.createStatement(currentSubject, property, currentObject);
                            currentSubject = currentObject;
                        }
                        final var property = ResourceFactory.createProperty(next.getResource("?p" + (currentPathLength - 1)).getURI());
                        path[currentPathLength - 1] = ResourceFactory.createStatement(currentSubject, property, object);
                        paths.add(path);
                    }
                }
            }
        }

        return paths.toArray(new Statement[paths.size()][]);
    }

    public static Rule[] createRules(Statement[][] paths, Statement head) {
        List<Rule> rules = new ArrayList<>(paths.length);
        for (final Statement[] path : paths) {
            // If a path has the length of one and is the same as the head, skip it.
            if (path.length == 1 && path[0].equals(head)) continue;

            String headString = String.format("(?e0, %s, ?e%d)", head.getPredicate().getURI(), path.length);
            String[] bodyStrings = new String[path.length];
            for (int i = 0; i < path.length; i++) {
                bodyStrings[i] = String.format("(?e%d, %s, ?e%d)", i, path[i].getPredicate().getURI(), i + 1);
            }
            rules.add(Rule.parseRule(String.join(", ", bodyStrings) + " -> " + headString + " ."));
        }
        return rules.toArray(new Rule[0]);
    }

    public static Rule[] createUnboundRules(Statement[][] paths, Statement head) {
        List<Rule> rules = new ArrayList<>(paths.length);
        for (final Statement[] path : paths) {
            // If a path has the length of one and is the same as the head, skip it.
            if (path.length == 1 && path[0].equals(head)) continue;

            String headString = String.format("(?e0, %s, ?e%d)", head.getPredicate().getURI(), path.length * 2 - 1);
            String[] bodyStrings = new String[path.length];
            for (int i = 0; i < path.length; i++) {
                bodyStrings[i] = String.format("(?e%d, %s, ?e%d)", i * 2, path[i].getPredicate().getURI(), (i * 2) + 1);
            }
            rules.add(Rule.parseRule(String.join(", ", bodyStrings) + " -> " + headString + " ."));
        }
        return rules.toArray(new Rule[0]);
    }

    public boolean doesRuleApply(Statement s) {
        return inferred.contains(s.asTriple());
    }

    public void setCounters(int numberOfCoveredExamples, int numberOfCoveredCounters, int numberOfCoveredExamplesUnbound, int numberOfCoveredCountersUnbound) {
        this.numberOfCoveredExamples = numberOfCoveredExamples;
        this.numberOfCoveredCounters = numberOfCoveredCounters;
        this.numberOfCoveredExamplesUnbound = numberOfCoveredExamplesUnbound;
        this.numberOfCoveredCountersUnbound = numberOfCoveredCountersUnbound;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public int getNumberOfCoveredExamples() {
        return numberOfCoveredExamples;
    }

    public int getNumberOfCoveredExamplesUnbound() {
        return numberOfCoveredExamplesUnbound;
    }

    public int getNumberOfCoveredCounters() {
        return numberOfCoveredCounters;
    }

    public int getNumberOfCoveredCountersUnbound() {
        return numberOfCoveredCountersUnbound;
    }

    public static WeightedRule[] loadRules(Path file, Model baseModel) throws IOException {
        WeightedRule[] rules;
        try (var reader = Files.newBufferedReader(file)) {
            final var ruleCount = Integer.parseInt(reader.readLine());
            rules = new WeightedRule[ruleCount];
            for (int i = 0; i < ruleCount; i++) {
                final var line = reader.readLine();
                final var split = line.split(";");
                rules[i] = new WeightedRule(Rule.parseRule(split[1]), Rule.parseRule(split[2]), split[0].trim().equals("positive"));
                rules[i].weight = Double.parseDouble(split[2]);
                // rules[i].inferred = rules[i].reasoner.bind(baseModel.getGraph());
            }
        }
        return rules;
    }

    public static void serializeRules(WeightedRule[] rules, Path file) throws IOException {
        try (var writer = Files.newBufferedWriter(file)) {
            writer.write(rules.length);
            writer.newLine();
            for (WeightedRule rule : rules) {
                DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
                df.setMaximumFractionDigits(20);
                writer.write(String.format("%s; %s; %s; %s",
                        rule.isPositive ? "positive" : "negative",
                        rule.rule.toShortString(),
                        rule.unboundRule.toShortString(),
                        df.format(rule.weight)));
                writer.newLine();
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        WeightedRule other = (WeightedRule) obj;
        return rule.equals(other.rule) && isPositive == other.isPositive;
    }

    @Override
    public int hashCode() {
        return rule.hashCode() + (isPositive ? 1 : 0);
    }
}

package org.upb_sw_factChecking.scoring;

import org.apache.jena.arq.querybuilder.ConstructBuilder;
import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.upb_sw_factChecking.app.SystemParameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Class containing the rule and additional information.
 */
public class WeightedRule {

    /**
     * Class to store result of local graph generation.
     *
     * @param graph the local graph
     * @param maxPathLength the maximum path length used to generate the local graph
     */
    public record LocalGraph(Model graph, int maxPathLength) {}

    public Rule rule;
    public boolean isPositive;
    public double weight;

    private int numberOfCoveredExamples;
    private int numberOfCoveredExamplesUnbound;
    private int numberOfCoveredCounters;
    private int numberOfCoveredCountersUnbound;

    private final static int ABSOLUTE_MAX_PATH_LENGTH = SystemParameters.ABSOLUTE_MAX_PATH_LENGTH;

    private static final Logger logger = LoggerFactory.getLogger(WeightedRule.class);

    public WeightedRule(Rule rule, boolean isPositive) {
        this.rule = rule;
        this.isPositive = isPositive;
        this.weight = 0.0;
    }

    public static WeightedRule[] generateRules(Model baseModel, Statement example, boolean isPositive, int maxPathLength) {
        LocalGraph localGraph = createLocalGraph(baseModel, example.getSubject(), example.getObject(), maxPathLength, ABSOLUTE_MAX_PATH_LENGTH);
        Statement[][] paths = createPaths(localGraph.graph, example.getSubject(), example.getObject(), localGraph.maxPathLength);
        Rule[] rules = createRules(paths, example);

        WeightedRule[] result = new WeightedRule[rules.length];
        for (int i = 0; i < rules.length; i++) {
            result[i] = new WeightedRule(rules[i], isPositive);
        }

        if (rules.length == 0) {
            logger.warn("No rules generated for example {}.", example);
        }

        return result;
    }

    public static LocalGraph createLocalGraph(Model baseModel, Resource subject, RDFNode object, int initialMaxPathLength, int absoluteMaxPathLength) {
        // Create local graph first.
        Model localGraph = ModelFactory.createDefaultModel();
        boolean foundSomething = false;

        // For each pathLength less than maxPathLength, create and execute a CONSTRUCT query, that generates a graph
        // that contains every path from the given subject to the given object with that given length.
        for (int currentPathLength = 1; (currentPathLength <= initialMaxPathLength || !foundSomething) && (currentPathLength <= absoluteMaxPathLength); currentPathLength++) {
            Long startTime = System.nanoTime();

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

                Long endTime = System.nanoTime();

                foundSomething = !temp.isEmpty();

                // If the path length is greater than the maximum path length and something has been found, update the
                // maximum path length.
                if (currentPathLength > initialMaxPathLength && foundSomething) {
                    initialMaxPathLength = currentPathLength;
                }
                Duration duration = Duration.of((endTime - startTime) / 1000000, ChronoUnit.MILLIS);
                if (duration.compareTo(SystemParameters.PATH_TIMEOUT) < 0 && currentPathLength == absoluteMaxPathLength && absoluteMaxPathLength < 100) {
                    absoluteMaxPathLength++;
                }
                if (currentPathLength >= initialMaxPathLength && !foundSomething && currentPathLength < absoluteMaxPathLength) {
                    // logger.warn("Path length of {} reached, but no path found. Extending to {}.", initialMaxPathLength, currentPathLength + 1);
                }
                localGraph.add(temp);
            }
        }

        // TODO: add unequal predicates

        return new LocalGraph(localGraph, initialMaxPathLength);
    }

    public static Statement[][] createPaths(Model localGraph, Resource subject, RDFNode object, int maxPathLength) {
        // Create paths.
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

    public boolean doesRuleApply(Model baseModel, Statement s) {
        if (!s.getPredicate().getURI().equals(this.rule.getHead()[0].toString().split(" ")[1].replace("@", "http://rdf.freebase.com/ns/"))) {
            // In this case rule doesn't apply at all.
            return false;
        }

        final var modifiedRule = Rule.parseRule(rule.toString()
                .replaceAll("\\?e0", s.getSubject().getURI())
                .replaceAll("\\?e" + rule.bodyLength(), s.getObject().isResource() ? s.getObject().asResource().getURI() : s.getObject().asLiteral().getLexicalForm()));
        final var reasoner = new GenericRuleReasoner(List.of(modifiedRule));
        reasoner.setMode(GenericRuleReasoner.FORWARD);
        reasoner.setOWLTranslation(false);
        reasoner.setTransitiveClosureCaching(false);
        return reasoner.bind(baseModel.getGraph()).contains(s.asTriple());
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

    public static WeightedRule[] loadRules(Path file) throws IOException {
        WeightedRule[] rules;
        try (var reader = Files.newBufferedReader(file)) {
            final var ruleCount = Integer.parseInt(reader.readLine());
            rules = new WeightedRule[ruleCount];
            for (int i = 0; i < ruleCount; i++) {
                final var line = reader.readLine();
                final var split = line.split(";");
                rules[i] = new WeightedRule(Rule.parseRule(split[1]), split[0].trim().equals("positive"));
                rules[i].weight = Double.parseDouble(split[2]);
            }
        }
        return rules;
    }

    public static void serializeRules(WeightedRule[] rules, Path file) throws IOException {
        try (var writer = Files.newBufferedWriter(file)) {
            writer.write(Integer.toString(rules.length));
            writer.newLine();
            for (WeightedRule rule : rules) {
                DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
                df.setMaximumFractionDigits(20);
                writer.write(String.format("%s; %s; %s",
                        rule.isPositive ? "positive" : "negative",
                        rule.rule.toShortString(),
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

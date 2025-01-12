package org.upb_sw_factChecking;

import org.apache.jena.arq.querybuilder.ConstructBuilder;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.upb_sw_factChecking.scoring.WeightedRule;

import java.util.Arrays;
import java.util.logging.Logger;


public class RuleGenerationTest {

    private final static Logger logger = Logger.getLogger(RuleGenerationTest.class.getName());

    @Test
    public void test() {
        final var model = RDFDataMgr.loadModel("reference-kg.nt");

        final var subject = ResourceFactory.createResource("http://rdf.freebase.com/ns/m.04w58");
        final var predicate = ResourceFactory.createProperty("http://rdf.freebase.com/ns/base.popstra.location.vacationers..base.popstra.vacation_choice.vacationer");
        final var object = ResourceFactory.createResource("http://rdf.freebase.com/ns/m.0261x8t");
        final var triple = ResourceFactory.createStatement(subject, predicate, object);


        final var localGraph = WeightedRule.createLocalGraph(model, subject, object, 4);
        final var paths = WeightedRule.createPaths(localGraph, subject, object, 4);

        final var rules = WeightedRule.createRules(paths, triple);
        Arrays.stream(rules).forEach(rule -> logger.info(rule.toShortString()));
    }

    @Test
    public void testConstructBuilder() {
        final var model = RDFDataMgr.loadModel("src/test/resources/reference-kg.nt");

        final var subject = ResourceFactory.createResource("http://rdf.freebase.com/ns/m.04w58");
        final var object = ResourceFactory.createResource("http://rdf.freebase.com/ns/m.0261x8t");

        for (int pathLength = 1; pathLength <= 3; pathLength++) {
            ConstructBuilder builder = new ConstructBuilder();
            if (pathLength == 1) {
                builder.addConstruct(subject, "?p", object);
                builder.addWhere(subject, "?p", object);
            } else {
                builder.addConstruct(subject, "?p0", "?e1");
                builder.addWhere(subject, "?p0", "?e1");
                for (int i = 1; i < pathLength - 1; i++) {
                    builder.addConstruct("?e" + i, "?p" + i, "?e" + (i + 1));
                    builder.addWhere("?e" + i, "?p" + i, "?e" + (i + 1));
                }
                builder.addConstruct("?e" + (pathLength - 1), "?p" + (pathLength - 1), object);
                builder.addWhere("?e" + (pathLength - 1), "?p" + (pathLength - 1), object);
            }
            logger.info(builder.toString());

            try (QueryExecution qexec = QueryExecutionFactory.create(builder.build(), model)) {
                Model result = qexec.execConstruct();
                result.listStatements().forEachRemaining(s -> logger.info(s.toString()));
            }
        }
    }


}

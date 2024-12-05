package org.upb_sw_factChecking;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.InfGraph;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.reasoner.ValidityReport;

public class FactChecker {
    private final Reasoner reasoner;
    private final Graph knownFacts;

    public FactChecker(Graph knownFacts) {
        this.knownFacts = knownFacts;
        reasoner = ReasonerRegistry.getOWLReasoner().bindSchema(knownFacts);
    }

    public double check(Statement toCheck) {
        if (knownFacts.contains(toCheck.asTriple()))
            return 1.0;
        Model tmp = ModelFactory.createDefaultModel();
        tmp.add(toCheck);
        InfGraph m = reasoner.bind(tmp.getGraph());
        ValidityReport v = m.validate();
        if (!v.isValid())
            return 0.0;
        return 0.5;
    }
}

package org.upb_sw_factChecking;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.InfGraph;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.reasoner.ValidityReport;

public class FactChecker {
    private final Reasoner knownReasoner;
    private final InfGraph inferred;

    public FactChecker(Graph knownFacts, Graph ontology) {
        knownReasoner = ReasonerRegistry.getOWLReasoner().bindSchema(ontology);
        Graph tmp = ModelFactory.createDefaultModel().getGraph();
        knownFacts.stream().forEach(tmp::add);
        ontology.stream().forEach(tmp::add);
        inferred = ReasonerRegistry.getOWLReasoner().bind(tmp);
    }

    public double check(Statement toCheck) {
        if (inferred.contains(toCheck.asTriple()))
            return 1.0;
        Model tmp = ModelFactory.createDefaultModel();
        tmp.add(toCheck);
        InfGraph m = knownReasoner.bind(tmp.getGraph());
        ValidityReport v = m.validate();
        if (!v.isValid())
            return 0.0;
        return 0.5;
    }
}

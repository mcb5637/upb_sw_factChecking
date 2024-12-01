package org.upb_sw_factChecking;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Statement;

public class FactChecker {
    public FactChecker(Graph knownFacts) {

    }

    public double check(Statement toCheck) {
        return 0.5;
    }
}

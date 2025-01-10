package org.upb_sw_factChecking;

import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.core.Var;

public class Rule {
    public Statement Head;
    public Statement[] Body;

    public Rule(Statement head, Statement[] body) {
        Head = head;
        Body = body;
    }
}

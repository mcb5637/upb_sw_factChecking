package org.upb_sw_factChecking;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.*;
import org.apache.jena.sparql.core.Var;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RuleGenerator {
    private final Model G;
    private static final int MAX_PATH_LENGTH = 3;

    public RuleGenerator(Model G) {
        this.G = G;
    }

    private Model buildLocalGraph(Statement s) {
        Model r = ModelFactory.createDefaultModel();
        addRelatedStatements(s, r);
        for (int i = 0; i < MAX_PATH_LENGTH/2; i++) {
            for (StmtIterator it = r.listStatements(); it.hasNext(); ) {
                addRelatedStatements(it.next(), r);
            }
        }
        return r;
    }

    private void addRelatedStatements(Statement s, Model r) {
        for (StmtIterator it = G.listStatements(); it.hasNext(); ) {
            Statement t = it.next();
            if (t.getSubject() == s.getSubject() || t.getObject() == s.getObject()) {
                r.add(t);
            }
        }
    }

    private List<Statement> buildPath(Model m, Statement s) {
        List<Statement> r = new ArrayList<>();
        Random rand = new Random();
        while (true) {
            Resource lastRes = s.getSubject();
            for (int i = 0; i < MAX_PATH_LENGTH; i++) {
                List<Statement> l = m.listStatements(lastRes, null, i == MAX_PATH_LENGTH - 1 ? s.getObject() : null).toList();
                if (l.isEmpty())
                    break;
                Statement toadd = l.get(rand.nextInt(l.size()));
                r.add(toadd);
                if (toadd.getObject() == s.getObject())
                    return r;
                lastRes = toadd.getSubject();
            }
            r.clear();
        }
    }

    public Rule generateRule(Statement s) {
        Model m = buildLocalGraph(s);
        var bl = buildPath(m, s);
        Statement[] b = new Statement[bl.size()];

        return new Rule(s, b);
    }
}

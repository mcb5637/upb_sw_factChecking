package org.upb_sw_factChecking;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;

public class KnownFacts {

    private final String knowledgeGraphPath;
    private final String classHierarchyPath;

    public KnownFacts(String classHierarchyPath, String knowledgeGraphPath){
        this.knowledgeGraphPath = knowledgeGraphPath;
        this.classHierarchyPath = classHierarchyPath;
    }

    public Graph createKnownFacts() {

        Model model = RDFDataMgr.loadModel(classHierarchyPath) ;
        RDFDataMgr.read(model, knowledgeGraphPath);
        Graph g = model.getGraph();
        return g;

    }
}




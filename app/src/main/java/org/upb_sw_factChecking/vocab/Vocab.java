package org.upb_sw_factChecking.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * General vocabulary class.
 */
public class Vocab {
    public final static Property AKSW_HAS_TRUTH_VALUE = ResourceFactory.createProperty("http://swc2017.aksw.org/hasTruthValue");
    public final static Property RDFSYNTAX_STATEMENT = ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement");
    public final static Property RDFSYNTAX_SUBJECT = ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#subject");
    public final static Property RDFSYNTAX_PREDICATE = ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate");
    public final static Property RDFSYNTAX_OBJECT = ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#object");
}

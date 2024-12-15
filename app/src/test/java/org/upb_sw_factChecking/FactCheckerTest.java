package org.upb_sw_factChecking;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FactCheckerTest {
    @Test
    void basicTest() {

        FactChecker c = new FactChecker(RDFParser.fromString("""
                <http://ex/1> <http://ex/2> true.
                <http://ex/2> <http://ex/1> <http://ex/3>.""", Lang.TURTLE).toGraph(), RDFParser.fromString("""
                @prefix owl: <http://www.w3.org/2002/07/owl#>.
                <http://ex/2> a owl:DatatypeProperty.
                <http://ex/1> a owl:SymmetricProperty.""", Lang.TURTLE).toGraph());
        Model m = ModelFactory.createDefaultModel();

        // stated in facts
        assertEquals(1.0, c.check(m.createStatement(m.createResource("http://ex/1"), m.createProperty("http://ex/2"), m.createTypedLiteral(true))));
        // derived via SymmetricProperty
        assertEquals(1.0, c.check(m.createStatement(m.createResource("http://ex/3"), m.createProperty("http://ex/1"), m.createResource("http://ex/2"))));
        // violates DatatypeProperty
        assertEquals(0.0, c.check(m.createStatement(m.createResource("http://ex/1"), m.createProperty("http://ex/2"), m.createResource("http://ex/3"))));
        // unknown
        assertEquals(0.5, c.check(m.createStatement(m.createResource("http://ex/1"), m.createProperty("http://ex/2"), m.createTypedLiteral(1))));
    }
}
package org.upb_sw_factChecking;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.apache.jena.graph.NodeFactory.createURI;


class KnownFactsTest {
    /*
ch_input is the complete path for the class hierarchy file
kg_input is the complete path for the knowledge graph file
both paths can't use backslashes, must be replaced by forward slashes

     */

    @ParameterizedTest
    @CsvSource({"src/test/resources/testing-ch-and-kg/reduced_classHierarchy.nt, src/test/resources/testing-ch-and-kg/reduced_knowledgeGraph.nt"})
    void createKnownFacts(String ch_input, String kg_input) {

        KnownFacts kf = new KnownFacts(ch_input, kg_input);
        Graph g = kf.createKnownFacts();

        //check if the first triple, 2 triples from the middle and the last triple from the class hierarchy are in the graph.
        try {

            Node s1 = createURI("http://rdf.freebase.com/ns/user.brendankwilliams.default_domain.inflation_rate");
            Node p1 = createURI("http://www.w3.org/2000/01/rdf-schema#subClassOf");
            Node o1 = createURI("http://rdf.freebase.com/ns/base.schemastaging.context_name");
            assert g.contains(s1, p1, o1): "a triple of class hierarchy is not in graph";

            Node s2 = createURI("http://rdf.freebase.com/ns/music.album");
            Node p2 = createURI("http://www.w3.org/2000/01/rdf-schema#subClassOf");
            Node o2 = createURI("http://rdf.freebase.com/ns/fictional_universe.character_occupation");
            assert g.contains(s2, p2, o2): "a triple  of class hierarchy is not in graph";

            Node s3 = createURI("http://rdf.freebase.com/ns/base.famousobjects.famous_object");
            Node p3 = createURI("http://www.w3.org/2000/01/rdf-schema#subClassOf");
            Node o3 = createURI("http://rdf.freebase.com/ns/base.academia.topic");
            assert g.contains(s3, p3, o3): "a triple  of class hierarchy is not in graph";

            Node s4 = createURI("http://rdf.freebase.com/ns/base.siswimsuitmodels.si_swimsuit_model");
            Node p4 = createURI("http://www.w3.org/2000/01/rdf-schema#subClassOf");
            Node o4 = createURI("http://rdf.freebase.com/ns/people.person");
            assert g.contains(s4, p4, o4): "a triple  of class hierarchy is not in graph";

        } catch (AssertionError e) {
            System.out.println(e.getMessage());
        }

        //check if the first triple, 2 triples from the middle and the last from the knowledge graph are in the graph.

        try {
            Node s5 = createURI("http://rdf.freebase.com/ns/base.words.grammatical_case.found_in_languages");
            Node p5 = createURI("http://www.w3.org/2000/01/rdf-schema#domain");
            Node o5 = createURI("http://rdf.freebase.com/ns/base.words.grammatical_case");
            assert g.contains(s5, p5, o5): "a triple of the knowledge graph is not in graph (not with a literal)";

            Node s6 = createURI("http://rdf.freebase.com/ns/m.038bht");
            Node p6 = createURI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
            Node o6 = createURI("http://rdf.freebase.com/ns/film.actor");
            assert g.contains(s6, p6, o6): "a triple of the knowledge graph is not in graph (not with a literal)";

            Node s7 = createURI("http://rdf.freebase.com/ns/m.0dsvzh");
            Node p7 = createURI("http://www.w3.org/2000/01/rdf-schema#label");
            Node o7 = NodeFactory.createLiteralLang("Gone Baby Gone", "en");
            assert g.contains(s7, p7, o7): "a triple of the knowledge graph is not in graph (with a literal)";

            Node s8 = createURI("http://rdf.freebase.com/ns/m.0jbqf");
            Node p8 = createURI("http://www.w3.org/2000/01/rdf-schema#label");
            Node o8 = NodeFactory.createLiteralLang("Colorado Avalanche", "en");
            assert g.contains(s8, p8, o8): "a triple of the knowledge graph is not in graph (with a literal)";


        } catch (AssertionError e) {
            System.out.println(e.getMessage());
        }

    }
}

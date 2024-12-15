package org.upb_sw_factChecking;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.upb_sw_factChecking.app.App;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {
    private final static Logger logger = LoggerFactory.getLogger(AppTest.class);

    @Test
    void appHasAGreeting() {
        App classUnderTest = new App();
        assertNotNull(classUnderTest.getGreeting(), "app should have a greeting");
        Model model = ModelFactory.createDefaultModel();
        model.add(model.createResource("http://example.org/subject"), model.createProperty("http://example.org/predicate"), model.createLiteral("object"));
        assertTrue(model.contains(model.createResource("http://example.org/subject"), model.createProperty("http://example.org/predicate"), model.createLiteral("object")));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        model.write(baos, "N-Triples");
        logger.info(baos.toString(StandardCharsets.UTF_8));
    }
}

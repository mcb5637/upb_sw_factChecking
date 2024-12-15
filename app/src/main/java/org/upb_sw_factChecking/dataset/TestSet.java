package org.upb_sw_factChecking.dataset;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.upb_sw_factChecking.vocab.Vocab;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TestSet {

    public record TestSetEntry(Resource factIri, Statement statement) {
        public TrainingSet.TrainingSetEntry toTrainingSetEntry(double truthValue) {
            return new TrainingSet.TrainingSetEntry(factIri, statement, truthValue);
        }
    }

    private final List<TestSet.TestSetEntry> entries = new ArrayList<>();

    private final static Logger logger = LoggerFactory.getLogger(TestSet.class);

    public TestSet(Path file) throws IOException {
        if (!Files.exists(file)) {
            logger.error("Training set file does not exist: {}", file);
            throw new IllegalArgumentException("Training set file does not exist: " + file);
        }
        try (final var reader = Files.newBufferedReader(file)) {
            loadStatements(reader);
        }
    }

    public TestSet(InputStream data) throws IOException {
        try (final var reader = new BufferedReader(new InputStreamReader(data))) {
            loadStatements(reader);
        }
    }

    private void loadStatements(BufferedReader reader) throws IOException {
        // Load statements from the file.
        // The file contains training data in N-Triples format.
        // Each training data contains a type and statement, therefore, each data is spread over 4 lines.

        String line;
        while ((line = reader.readLine()) != null) {
            Model model = ModelFactory.createDefaultModel();
            model.read(new StringReader(line), null, "N-Triples");
            for (int i = 0; i < 3; i++) {
                model.read(new StringReader(reader.readLine()), null, "N-Triples");
            }
            final var factIRI = model.listSubjectsWithProperty(RDF.type, Vocab.RDFSYNTAX_STATEMENT).next();
            final var subject = model.listObjectsOfProperty(Vocab.RDFSYNTAX_SUBJECT).next();
            final var predicate = ResourceFactory.createProperty(model.listObjectsOfProperty(Vocab.RDFSYNTAX_PREDICATE).next().toString());
            final var object = model.listObjectsOfProperty(Vocab.RDFSYNTAX_OBJECT).next();
            final var statement = model.createStatement((Resource) subject, predicate, object);
            entries.add(new TestSet.TestSetEntry(factIRI, statement));
        }
    }

    public List<TestSet.TestSetEntry> getEntries() {
        return entries;
    }

}

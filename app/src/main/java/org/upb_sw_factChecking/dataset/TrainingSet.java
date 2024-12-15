package org.upb_sw_factChecking.dataset;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.upb_sw_factChecking.vocab.Vocab;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a training set.
 * Also reused to store results from test set.
 */
public class TrainingSet {

    public record TrainingSetEntry(Resource factIRI, Statement statement, double truthValue) {
        public Statement getTruthValueStatement() {
            return ResourceFactory.createStatement(factIRI, Vocab.AKSW_HAS_TRUTH_VALUE, ResourceFactory.createTypedLiteral(truthValue));
        }
    }

    private final List<TrainingSetEntry> entries = new ArrayList<>();

    private final static Logger logger = LoggerFactory.getLogger(TrainingSet.class);

    public TrainingSet(Path file) throws IOException {
        if (!Files.exists(file)) {
            logger.error("Training set file does not exist: {}", file);
            throw new IllegalArgumentException("Training set file does not exist: " + file);
        }
        try (final var reader = Files.newBufferedReader(file)) {
            loadStatements(reader);
        }
    }

    public TrainingSet(InputStream data) throws IOException {
        try (final var reader = new BufferedReader(new InputStreamReader(data))) {
            loadStatements(reader);
        }
    }

    private void loadStatements(BufferedReader reader) throws IOException {
        // The file contains training data in N-Triples format.
        // Each training data contains a type, statement, and truth value, therefore, each data is spread over 5 lines.

        String line;
        while ((line = reader.readLine()) != null) {
            Model model = ModelFactory.createDefaultModel();
            model.read(new StringReader(line), null, "N-Triples");
            for (int i = 0; i < 4; i++) {
                model.read(new StringReader(reader.readLine()), null, "N-Triples");
            }
            final var factIRI = model.listSubjectsWithProperty(RDF.type, Vocab.RDFSYNTAX_STATEMENT).next();
            final var subject = model.listObjectsOfProperty(Vocab.RDFSYNTAX_SUBJECT).next();
            final var predicate = ResourceFactory.createProperty(model.listObjectsOfProperty(Vocab.RDFSYNTAX_PREDICATE).next().toString());
            final var object = model.listObjectsOfProperty(Vocab.RDFSYNTAX_OBJECT).next();
            final var statement = model.createStatement((Resource) subject, predicate, object);
            final var truthValue = model.listObjectsOfProperty(Vocab.AKSW_HAS_TRUTH_VALUE).next().asLiteral().getDouble();
            entries.add(new TrainingSetEntry(factIRI, statement, truthValue));
        }
    }

    public List<TrainingSetEntry> getEntries() {
        return entries;
    }

    public static void serializeToResultFile(List<TrainingSetEntry> entries, Path path) throws IOException {
        Files.write(path, entries.stream().map(entry -> {
            final var m = ModelFactory.createDefaultModel();
            final var baos = new ByteArrayOutputStream();
            m.add(entry.getTruthValueStatement());
            m.write(baos, "Turtle");
            return baos.toString(StandardCharsets.UTF_8);
        }).toList(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}

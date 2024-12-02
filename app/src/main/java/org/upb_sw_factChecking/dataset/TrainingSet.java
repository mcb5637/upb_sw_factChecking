package org.upb_sw_factChecking.dataset;

import org.apache.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TrainingSet {

    public record TrainingSetEntry(Model model, Statement statement, double truthValue) {}

    private final Path file;
    private final List<TrainingSetEntry> entries = new ArrayList<>();

    private final static Logger logger = LoggerFactory.getLogger(TrainingSet.class);

    public TrainingSet(Path file) {
        this.file = file;
        if (!Files.exists(file)) {
            logger.error("Training set file does not exist: {}", file);
            throw new IllegalArgumentException("Training set file does not exist: " + file);
        }
    }

    public void loadStatements() {
        // Load statements from the file.
        // The file contains training data in N-Triples format.
        // Each training data contains a type, statement, and truth value, therefore, each data is spread over 5 lines.

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Model model = ModelFactory.createDefaultModel();
                model.read(new StringReader(line), null, "N-Triples");
                for (int i = 0; i < 4; i++) {
                    model.read(new StringReader(reader.readLine()), null, "N-Triples");
                }
                final var subject = model.listObjectsOfProperty(
                            ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#subject"))
                        .next();
                final var predicate = ResourceFactory.createProperty(
                        model.listObjectsOfProperty(
                            ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate"))
                        .next().toString());

                final var object = model.listObjectsOfProperty(
                            ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#object"))
                        .next();
                final var statement = model.createStatement((Resource) subject, predicate, object);
                final var truthValue = model.listObjectsOfProperty(
                            ResourceFactory.createProperty("http://swc2017.aksw.org/hasTruthValue"))
                        .next().asLiteral().getDouble();
                entries.add(new TrainingSetEntry(model, statement, truthValue));
            }

        } catch (IOException e) {
            logger.error("Error reading training set file", e);
        }
    }

    public List<TrainingSetEntry> getEntries() {
        return entries;
    }
}

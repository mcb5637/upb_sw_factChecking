package org.upb_sw_factChecking.dataset;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

// import static org.junit.jupiter.api.Assertions.*;

class TrainingSetTest {

    @Test
    void loadStatements() { //feeds in the first two facts from the TRAININGSET and prints the returned Arraylist
        try {
            File tempFile = File.createTempFile("shorttestinput_", "_1");
            Path path = Paths.get(String.valueOf(tempFile));

            // first two facts from the training set
            String shortTrainingData = "<http://dice-research.org/data/fb15k-237.ttl#0> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement> .\n" +
                    "<http://dice-research.org/data/fb15k-237.ttl#0> <http://www.w3.org/1999/02/22-rdf-syntax-ns#subject> <http://rdf.freebase.com/ns/m.0bmssv> .\n" +
                    "<http://dice-research.org/data/fb15k-237.ttl#0> <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> <http://rdf.freebase.com/ns/film.film.featured_film_locations> .\n" +
                    "<http://dice-research.org/data/fb15k-237.ttl#0> <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> <http://rdf.freebase.com/ns/m.080h2> .\n" +
                    "<http://dice-research.org/data/fb15k-237.ttl#0> <http://swc2017.aksw.org/hasTruthValue> \"1.0\"^^<http://www.w3.org/2001/XMLSchema#double> .\n" +
                    "<http://dice-research.org/data/fb15k-237.ttl#1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement> .\n" +
                    "<http://dice-research.org/data/fb15k-237.ttl#1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#subject> <http://rdf.freebase.com/ns/m.0cjsxp> .\n" +
                    "<http://dice-research.org/data/fb15k-237.ttl#1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> <http://rdf.freebase.com/ns/people.person.nationality> .\n" +
                    "<http://dice-research.org/data/fb15k-237.ttl#1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> <http://rdf.freebase.com/ns/m.09c7w0> .\n" +
                    "<http://dice-research.org/data/fb15k-237.ttl#1> <http://swc2017.aksw.org/hasTruthValue> \"1.0\"^^<http://www.w3.org/2001/XMLSchema#double> .\n";
            Files.write(
                    path,
                    shortTrainingData.getBytes(),
                    StandardOpenOption.APPEND);

            TrainingSet shortInput = new TrainingSet(path);
            List<TrainingSet.TrainingSetEntry> allEntries = shortInput.getEntries();
            System.out.print(allEntries);
            Files.delete(path);
        }
        catch(Exception e )
        {
            System.out.print("Something went wrong :)");
            System.out.println(e.getMessage());

        }

    }

    @Test
    void getEntries() {
    }
}
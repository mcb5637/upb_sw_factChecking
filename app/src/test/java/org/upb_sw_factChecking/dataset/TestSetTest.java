package org.upb_sw_factChecking.dataset;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

class TestSetTest {

    @Test
    void loadStatementsAndGetEntries() { //feeds in the first two facts from the TESTSET and prints the returned Arraylist

        try {
        File tempFile = File.createTempFile("shorttestinput_", "_1");
        Path path = Paths.get(String.valueOf(tempFile));

        // first two facts from the testing set
        String shortTestData = "<http://dice-research.org/data/fb15k-237.ttl#3> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement> .\n" +
                "<http://dice-research.org/data/fb15k-237.ttl#3> <http://www.w3.org/1999/02/22-rdf-syntax-ns#subject> <http://rdf.freebase.com/ns/m.0y_9q> .\n" +
                "<http://dice-research.org/data/fb15k-237.ttl#3> <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> <http://rdf.freebase.com/ns/film.film.genre> .\n" +
                "<http://dice-research.org/data/fb15k-237.ttl#3> <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> <http://rdf.freebase.com/ns/m.04cb4x> .\n" +
                "<http://dice-research.org/data/fb15k-237.ttl#5> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement> .\n" +
                "<http://dice-research.org/data/fb15k-237.ttl#5> <http://www.w3.org/1999/02/22-rdf-syntax-ns#subject> <http://rdf.freebase.com/ns/m.0342h> .\n" +
                "<http://dice-research.org/data/fb15k-237.ttl#5> <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> <http://rdf.freebase.com/ns/music.instrument.instrumentalists> .\n" +
                "<http://dice-research.org/data/fb15k-237.ttl#5> <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> <http://rdf.freebase.com/ns/m.01vrnsk> .\n";
        Files.write(
                path,
                shortTestData.getBytes(),
                StandardOpenOption.APPEND);


        TestSet shortInput = new TestSet(path);
        shortInput.loadStatements();
        List<TestSet.TestSetEntry> allEntries = shortInput.getEntries();
        System.out.print(allEntries);
        Files.delete(path);
        }
        catch(Exception e )
        {
            System.out.print("Something went wrong :)");
            System.out.println(e.getMessage());

        }

    }

}
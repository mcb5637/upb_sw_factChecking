package org.upb_sw_factChecking;

import org.apache.commons.io.IOUtils;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.graph.GraphFactory;

import java.io.*;
import static org.apache.jena.graph.NodeFactory.createURI;

public class KnownFacts {

    private final String knowledgeGraphPath;
    private final String classHierarchyPath;
    Graph g = GraphFactory.createDefaultGraph();

    public KnownFacts(String classHierarchyPath, String knowledgeGraphPath){
        this.knowledgeGraphPath = knowledgeGraphPath;
        this.classHierarchyPath = classHierarchyPath;
    }

    public Graph createKnownFacts() {

        String[] files = {classHierarchyPath, knowledgeGraphPath};
        for (String file: files) { // repeat for both files

            try (FileInputStream inputStream = new FileInputStream(file)) {
                String everything = IOUtils.toString(inputStream);

                BufferedReader reader = new BufferedReader(new StringReader(everything));
                String line = reader.readLine();

                while (line != null) {

                    if (line.matches("^<.*$")) { //reads subject and property of triple

                        String subject = line.substring(1, line.indexOf(" ") - 1);
                        String line_s = line.substring(line.indexOf(" ") + 2);

                        String prop = line_s.substring(0, line_s.indexOf(">"));

                        Node s = createURI(subject);
                        Node p = createURI(prop);

                        if (line.contains("@")) { //reads literal of triple
                            String line_p = line_s.substring(line_s.indexOf(">") + 3, line_s.length() - 2);
                            String literal = line_p.substring(0, line_p.length() - 4);
                            String language = line_p.substring(line_p.length() - 2); //
                            Node o = NodeFactory.createLiteralLang(literal, language);
                            g.add(s, p, o);

                        } else if (line.matches("^<.*$")) { //reads object, thats not a literal
                            String object = line_s.substring(line_s.indexOf(">") + 3, line_s.length() - 3);
                            Node o = createURI(object);
                            g.add(s, p, o);
                        }

                        line = reader.readLine();

                    } else {
                        System.out.println("line does not have a triple");
                    }

                }
            } catch (Error e) {
                System.out.println("Error");
                System.out.println(e.getMessage());

            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return g;




    }
}




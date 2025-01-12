package org.upb_sw_factChecking;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.upb_sw_factChecking.scoring.WeightedRule;

import java.io.IOException;
import java.nio.file.Path;

public class EvidenceTruthScoring {
    // finds the strongest positive and negative rule from a rule file that covers the statement and computes the truthScore
    Path ruleFile;
    WeightedRule[] rules;

    public EvidenceTruthScoring(Path ruleFile){
        Model m = ModelFactory.createDefaultModel();
        try {
            this.ruleFile = ruleFile;
            rules = WeightedRule.loadRules(ruleFile, m);
        } catch (IOException e) {
            System.out.println("Problem loading the rules" + e.getMessage());
        }
    }


    public double[] findStrongestCoveringRules(Statement statement){
        //the lower the weight, the stronger the rule: min 0.0, max 1.0
        double currentMinPosW = 1.0;
        double currentMinNegW = 1.0;
        //iterate through all rules
        for (WeightedRule r: rules){
            //check if rule covers statement
            boolean covers = r.doesRuleApply(statement);
            if(covers){
                //update weight minimum
                if (r.weight < currentMinPosW && r.isPositive){
                    currentMinPosW = r.weight;
                }
                else if (r.weight < currentMinNegW && !r.isPositive) {
                    currentMinNegW = r.weight;
                }
            }
        }
        //return the weight of the strongest positive and negative rule, that covers the statement
        return new double[]{currentMinPosW,currentMinNegW};
    }


    public static double computeScore(double[] strongest) {
        final double truthScore;
        double minPositiveW = strongest[0];
        double minNegativeW = strongest[1];

        if (minPositiveW != 1.0){
            // "If positive evidence for a given statement are found in a knowledge graph, then we use
            //0.0 instead of the weight of the strongest negative rule"
            truthScore = (1-minPositiveW-2)/2;
        }
        else{
            truthScore = ((1-minPositiveW)-(1-minNegativeW)+1)/2;
        }
        return truthScore;

    }
}
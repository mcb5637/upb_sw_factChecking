package org.upb_sw_factChecking;

import org.apache.jena.rdf.model.Statement;


public class EvidenceTruthScoring {
    // finds the strongest positive and negative rule that covers the statement and computes the truthscore
    double maxPositiveW;
    double maxNegativeW;
    Statement statement;
    //Rule[] rules; TODO add Rulelist/array etc



    public EvidenceTruthScoring(Statement statement, String[] rules){ //TODO change rules type
        this.statement = statement;
        //this.rules = rules;

    }

    public double computeScore(double maxPositiveW, double maxNegativeW) {
        final double truthScore;
        if (maxPositiveW != 0){
            // "If positive evidence for a given statement are found in a knowledge graph, then we use
            //0.0 instead of the weight of the strongest negative rule"
            truthScore = (1-maxPositiveW-2)/2;
        }
        else{
            truthScore = ((1-maxPositiveW)-(1-maxNegativeW)+1)/2;
        }
        return truthScore;

    }
}

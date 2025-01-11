package org.upb_sw_factChecking;

import org.apache.jena.rdf.model.Statement;


public class EvidenceTruthScoring {
    // finds the strongest positive and negative rule that covers the statement and computes the truthScore
    double minPositiveW;
    double minNegativeW;
    Statement statement;
    //Rule[] rules; TODO add Rulelist/array etc



    public EvidenceTruthScoring(Statement statement, String[] rules){ //TODO change rules type
        this.statement = statement;
        //this.rules = rules;

    }

    public void findStrongestCoveringRules(){
        double ruleWeight = 1.0;
        double currentMinPosW = 1.0;
        double currentMinNegW = 1.0;
        // TODO iterate through all rules. If rule covers Statement, compute weight "ruleWeight"/look up weight
        // TODO check if rule is pos rule or neg rule
        // depends on how rules are implemented

        //the lower the weight, the stronger the rule

        // if ruletype pos:
        if (ruleWeight < currentMinPosW){
            currentMinPosW = ruleWeight;
        }
        // if ruletype neg:
        if (ruleWeight < currentMinPosW){
            currentMinNegW = ruleWeight;
        }
        // change class variables at the end
        minPositiveW = currentMinPosW;
        minNegativeW = currentMinNegW;
    }


    public double computeScore(double minPositiveW, double minNegativeW) {
        final double truthScore;
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

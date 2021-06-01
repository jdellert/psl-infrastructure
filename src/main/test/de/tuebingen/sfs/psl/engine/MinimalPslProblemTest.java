package de.tuebingen.sfs.psl.engine;

import org.linqs.psl.model.rule.Rule;

public class MinimalPslProblemTest {

    // TODO make into test
    public static void main(String... args) {
        ProblemManager problemManager = ProblemManager.defaultProblemManager();
        DatabaseManager dbManager = problemManager.getDbManager();
        problemManager.getPartitionManager().verbose = true;
        MinimalPslProblem prob = new MinimalPslProblem(dbManager, "MinimalPslProblem");
        for (Rule rule : prob.listRules()) {
            System.out.println(rule);
        }
        InferenceResult result = problemManager.registerAndRunProblem(prob);
        RuleAtomGraph rag = result.getRag();
        rag.printToStream(System.out);
        result.printInferenceValues();
        dbManager.printTable("small");
        dbManager.printTable("antecedent");
        dbManager.printTable("consequent");
    }

}

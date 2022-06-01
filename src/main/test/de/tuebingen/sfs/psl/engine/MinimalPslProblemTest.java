/*
 * Copyright 2018–2022 University of Tübingen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

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
package de.tuebingen.sfs.psl.examples.livesknows;

import de.tuebingen.sfs.psl.engine.DatabaseManager;
import de.tuebingen.sfs.psl.engine.InferenceResult;
import de.tuebingen.sfs.psl.engine.ProblemManager;
import de.tuebingen.sfs.psl.engine.RuleAtomGraph;
import de.tuebingen.sfs.psl.util.data.Pair;
import de.tuebingen.sfs.psl.util.log.InferenceLogger;

import java.util.Collections;

public class EntryClass {

    public static Pair<SamplePslProblem, InferenceResult> runInference(){
        // Create the classes in charge of managing the database partitions and saving the inference results:
        ProblemManager problemManager = ProblemManager.defaultProblemManager();
        DatabaseManager dbManager = problemManager.getDbManager();
        String problemId = problemManager.getNewId("SampleProblem");

        // Declare the predicates and rules:
        SamplePslProblem problem = new SamplePslProblem(dbManager, problemId);
        // Generate ground atoms:
        SampleIdeaGenerator ideaGen = new SampleIdeaGenerator(problem);
        ideaGen.generateAtoms("/examples/livesknows/addresses.csv");

        // Run the inference and extract the results:
        problemManager.registerProblem(problemId, problem);
        InferenceLogger logger = new InferenceLogger();
        problemManager.preparePartitionsAndRun(Collections.singletonList(problem), logger);
        InferenceResult result = problemManager.getLastResult(problemId);
        return new Pair<>(problem, result);
    }

    public static void main(String[] args) {
        Pair<SamplePslProblem, InferenceResult> problemAndResult = runInference();
        SamplePslProblem problem = problemAndResult.first;
        InferenceResult result = problemAndResult.second;

        // Inspect the results:
        RuleAtomGraph rag = result.getRag();
        problem.printRules(System.out);
        // Print each ground atom -- ground rule combination
        // together with the kind of pressure the rule exerts on the atom:
        // +~ upward pressure
        // -~ downward pressure
        // =~ pressure in both directions
        rag.printToStream(System.out);
        // Print the (inferred and fixed) atom values:
        result.printInferenceValues();
    }
}

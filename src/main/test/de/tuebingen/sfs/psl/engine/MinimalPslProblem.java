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

import org.linqs.psl.model.rule.GroundRule;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class MinimalPslProblem extends PslProblem {

    public MinimalPslProblem(DatabaseManager dbManager, String name) {
        super(dbManager, name);
    }

    @Override
    public void declarePredicates() {
        declareOpenPredicate("antecedent", 1);
        declareOpenPredicate("consequent", 1);
        declareOpenPredicate("small", 1);
    }

    @Override
    public void pregenerateAtoms() {
        addObservation("antecedent", "a");
        addObservation("antecedent", "b");
        addTarget("consequent", "a");
        addTarget("consequent", "b");
        addTarget("small", "a");
        addTarget("small", "b");

//		addTarget("antecedent", 0.8, "a");
//		addTarget("antecedent", 0.8, "b");
//		addObservation("consequent", 0.3, "a");
//		addObservation("consequent", 0.3, "b");
//		addObservation("small", "a");
//		addObservation("small", "b");
    }

    @Override
    public void addInteractionRules() {
//		addRule("sum", "small(+X) <= 0.5 .");
//		addRule("prior", "~consequent(X) .");
//		addRule("aToC", "antecedent(X) -> consequent(X) .");
        addRule("sum", "small(+X) <= 0.5 .");
        addRule("prior", "1: ~consequent(X)");
        addRule("aToC", "2: antecedent(X) -> consequent(X)");
    }

    @Override
    public InferenceResult call() throws Exception {
        List<List<GroundRule>> groundRules = runInference(true);
        RuleAtomGraph.GROUNDING_OUTPUT = true;
        RuleAtomGraph.ATOM_VALUE_OUTPUT = true;
        Map<String, Double> valueMap = extractResultsForAllPredicates(false);
        RuleAtomGraph rag = new RuleAtomGraph(this, new RagFilter(valueMap), groundRules);
        return new InferenceResult(rag, valueMap);
    }

    @Override
    public Set<AtomTemplate> declareAtomsForCleanUp() {
        return null;
    }

}

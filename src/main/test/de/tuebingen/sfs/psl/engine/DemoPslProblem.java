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

public class DemoPslProblem extends PslProblem {
    String openPredName;
    String closedPredName;

    // The openPredName and closedPredName are for testing purposes only!
    public DemoPslProblem(DatabaseManager dbManager, String name, String openPredName, String closedPredName) {
        super(dbManager, name);
        this.openPredName = openPredName;
        this.closedPredName = closedPredName;
        declareNamedDemoPredicates();
        pregenerateNamedDemoAtoms();
    }

    @Override
    public void declarePredicates() {
    }

    public void declareNamedDemoPredicates() {
        declareOpenPredicate(openPredName, 2);
        declareOpenPredicate(closedPredName, 2);
    }

    @Override
    public void pregenerateAtoms() {
    }

    public void pregenerateNamedDemoAtoms() {
        addTarget(openPredName, openPredName.substring(7), "aa");
        addTarget(openPredName, openPredName.substring(7), "bb");
        addTarget(openPredName, openPredName.substring(7), "cc");

        addObservation(closedPredName, "aa", "xx");
        addObservation(closedPredName, "aa", "yy");
        addObservation(closedPredName, "aa", "zz");
    }

    public void addDemoObservation(String arg1, String arg2) {
        addObservation(closedPredName, arg1, arg2);
    }

    @Override
    public void addInteractionRules() {
    }

    @Override
    public Set<AtomTemplate> declareAtomsForCleanUp() {
        return null;
    }

    @Override
    public InferenceResult call() throws Exception {
        addInteractionRules();
        List<List<GroundRule>> groundRules = runInference(true);
//		this.atoms.closeDatabase();
        RuleAtomGraph.GROUNDING_OUTPUT = true;
        RuleAtomGraph.ATOM_VALUE_OUTPUT = true;
        Map<String, Double> valueMap = extractResultsForAllPredicates();
        RuleAtomGraph rag = new RuleAtomGraph(this, new RagFilter(valueMap), groundRules);
        return new InferenceResult(rag, valueMap);
    }

}

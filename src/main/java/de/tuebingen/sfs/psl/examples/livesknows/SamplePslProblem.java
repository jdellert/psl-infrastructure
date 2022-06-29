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

import de.tuebingen.sfs.psl.engine.AtomTemplate;
import de.tuebingen.sfs.psl.engine.DatabaseManager;
import de.tuebingen.sfs.psl.engine.PslProblem;

import java.util.HashSet;
import java.util.Set;

public class SamplePslProblem extends PslProblem {

    public SamplePslProblem(DatabaseManager dbManager, String id) {
        super(dbManager, id);
    }

    @Override
    public void declarePredicates() {
        declareClosedPredicate(new LivesPred());
        declareOpenPredicate(new KnowsPred());
    }

    @Override
    public void addInteractionRules() {
        addRule(new LivesToKnowsRule(this));
        addRule(new KnowsSymmetryConstraint(this));
    }

    @Override
    public void pregenerateAtoms() {
    }

    @Override
    public Set<AtomTemplate> declareAtomsForCleanUp() {
        return new HashSet<>();
    }

}

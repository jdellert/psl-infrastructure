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
package de.tuebingen.sfs.psl.eval;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.util.data.Tuple;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GoldStandard {

    int nOfPredicates();

    PredicateEvaluationTemplate[] getPredicates();

    boolean check(PredicateEvaluationTemplate predicate, Tuple atom);

    boolean alreadyFound(PredicateEvaluationTemplate predicate, Tuple atom);

    Set<Arguments> missingAtoms(PredicateEvaluationTemplate predicate);

    void additionalEvaluation(PredicateEvaluationTemplate predicate, Map<Tuple, Double> foundAtoms,
                              Map<Tuple, Double> foundNotInGSAtoms, Set<Arguments> missingAtoms,
                              PslProblem problem, PrintStream pStream);

    List<ModelEvaluator.TabularEvaluationEntry> additionalTabularEvaluation(
            PredicateEvaluationTemplate predicate, Map<Tuple, Double> foundAtoms,
            Map<Tuple, Double> foundNotInGSAtoms, Set<Arguments> missingAtoms,
            PslProblem problem, PrintStream pStream);

    void reset();
}

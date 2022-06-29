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

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.engine.RuleAtomGraph;
import de.tuebingen.sfs.psl.talk.BeliefScale;
import de.tuebingen.sfs.psl.talk.ConstantRenderer;
import de.tuebingen.sfs.psl.talk.rule.TalkingArithmeticConstraint;
import de.tuebingen.sfs.psl.util.data.Tuple;

public class KnowsSymmetryConstraint extends TalkingArithmeticConstraint {

    static String RULE = "Knows(P1,P2) = Knows(P2,P1)";
    static String VERBALIZATION = "The 'knows' relation is symmetrical.";

    public KnowsSymmetryConstraint(PslProblem pslProblem) {
        super("KnowsSymmetry", RULE, pslProblem, VERBALIZATION);
    }


    @Override
    public String generateExplanation(ConstantRenderer renderer, String groundingName, String contextAtom,
                                      RuleAtomGraph rag, boolean whyExplanation) {
        // Extract the ground atom that *isn't* the context atom.
        String inverseAtom = null;
        double inverseBelief = -1.0;
        for (Tuple atomToStatus : rag.getLinkedAtomsForGroundingWithLinkStatusAsList(groundingName)) {
            String atom = atomToStatus.get(0);
            if (atom.equals(contextAtom)) {
                continue;
            }
            inverseAtom = atom;
            inverseBelief = rag.getValue(atom);
        }

        StringBuilder sb = new StringBuilder();
        boolean similar = Math.abs(inverseBelief - rag.getValue(contextAtom)) < RuleAtomGraph.DISSATISFACTION_PRECISION;
        sb.append("The 'knows' relationship is symmetric, ").append(similar ? "and" : "but");
        sb.append(" the \\url[").append(escapeForURL("inverse similarity")).append("]{").append(inverseAtom);
        sb.append("} is ");
        if (similar) {
            sb.append("also ");
        }
        sb.append(BeliefScale.verbalizeBeliefAsAdjectiveHigh(inverseBelief));
        if (!similar) {
            sb.append(" (%.2f)".formatted(inverseBelief));
        }
        sb.append(".");
        return sb.toString();
    }
}

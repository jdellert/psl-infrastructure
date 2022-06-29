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
import de.tuebingen.sfs.psl.talk.rule.TalkingLogicalRule;
import de.tuebingen.sfs.psl.util.data.Tuple;

public class LivesToKnowsRule extends TalkingLogicalRule {

    static double WEIGHT = 1.0;
    static String RULE = "Lives(P1,L) & Lives(P2,L) & (P1 != P2) -> Knows(P1,P2)";
    static String VERBALIZATION = "If there is evidence that two people live at the same address, " +
            "this makes it more likely that they know each other.";

    public LivesToKnowsRule(PslProblem pslProblem) {
        super("LivesToKnows", WEIGHT, RULE, pslProblem, VERBALIZATION);
    }

    @Override
    public String generateExplanation(ConstantRenderer renderer, String groundingName, String contextAtom,
                                      RuleAtomGraph rag, boolean whyExplanation) {
        // Extract the ground atoms.
        String lives1 = null;
        String[] lives1Args = null;
        String lives2 = null;
        String[] lives2Args = null;
        String knows = null;
        String[] knowsArgs = null;
        for (Tuple atomToStatus : rag.getLinkedAtomsForGroundingWithLinkStatusAsList(groundingName)) {
            String atom = atomToStatus.get(0);
            String[] atomArgs = atom.substring(atom.indexOf('(') + 1, atom.length() - 1).split(",\\s?");
            if (atom.startsWith("K")) {
                knows = atom;
                knowsArgs = atomArgs;
            } else if (lives1 != null) {
                lives2 = atom;
                lives2Args = atomArgs;
            } else {
                lives1 = atom;
                lives1Args = atomArgs;
            }
        }
        double lives1Value = rag.getValue(lives1);
        double lives2Value = rag.getValue(lives2);
        double knowsValue = rag.getValue(knows);
        String p1 = lives1Args[0];
        String p2 = lives2Args[0];
        String l = ((SampleConstantRenderer) renderer).prettyHouseNumber(lives1Args[1]);

        StringBuilder sb = new StringBuilder();
        sb.append(VERBALIZATION).append(" ");

        // If the context atom is the consequent, we explain why the value isn't any lower.
        // "P1 probably lives in L, and P2 probably lives there as well, therefore they probably know each other."
        if (contextAtom.equals(knows)) {
            sb.append("It is ").append(BeliefScale.verbalizeBeliefAsAdjective(lives1Value)).append(" that \\url[");
            sb.append(escapeForURL(new LivesPred().verbalizeIdeaAsSentence(renderer, lives1Args)));
            sb.append("]{").append(lives1).append("}, and \\url[");
            if (lives1Value <= 0.5 && lives2Value <= 0.5) {
                // "It is unlikely that P1 lives in L, and it is also very unlikely that P2 lives in L"
                sb.append("it is also ").append(escapeForURL(BeliefScale.verbalizeBeliefAsAdjective(lives2Value)));
                sb.append(" that ").append(new LivesPred().verbalizeIdeaAsSentence(renderer, lives2Args));
            } else if (lives1Value > 0.5 && lives2Value > 0.5) {
                // "It is likely that P1 lives in L, and P2 probably lives in L as well"
                sb.append(escapeForURL(new LivesPred().verbalizeIdeaAsSentence(renderer, lives2Value, lives2Args)));
                sb.append(" as well");
            } else {
                sb.append(escapeForURL(new LivesPred().verbalizeIdeaAsSentence(renderer, lives2Value, lives2Args)));
            }
            sb.append("]{").append(lives2).append("}, ");
            double minSim = lives1Value + lives2Value - 1;
            if (minSim < RuleAtomGraph.DISSATISFACTION_PRECISION) {
                sb.append(
                        " however, changing the estimate for whether they know one another would actually not cause a rule violation");
            } else {
                sb.append(" therefore ").append(new KnowsPred().verbalizeIdeaAsNpAlt(p1, p2)).append(" should ");
                sb.append(BeliefScale.verbalizeBeliefAsMinimumSimilarityInfinitive(minSim));
            }
            return sb.append(".").toString();
        }

        // If the context atom is one of the antecedents, we explain why its value isn't any higher.

        // If the ground rule is trivially satisfied because Knows(P1,P2) has the maximum possible score,
        // we explain that this is the case.
        if (knowsValue > 1 - RuleAtomGraph.DISSATISFACTION_PRECISION) {
            sb.append("However, since we are already certain that ").append(p1).append(" and ").append(p2);
            sb.append(" know each other ");
            sb.append(", changing either of the address values wouldn't cause a rule violation. (\\url[");
            sb.append(new LivesPred().verbalizeIdeaAsSentence(renderer, lives1Value, lives1Args));
            sb.append("]{").append(lives1).append("} and \\url[");
            sb.append(new LivesPred().verbalizeIdeaAsSentence(renderer, lives2Value, lives2Args));
            sb.append("]{").append(lives2).append("}.)");
            return sb.toString();
        }

        sb.append("Since \\url[");
        if (contextAtom.equals(lives1)) {
            sb.append(escapeForURL(new LivesPred().verbalizeIdeaAsSentence(renderer, lives2Value, lives2Args)));
            sb.append("]{").append(lives2);
        } else {
            sb.append(escapeForURL(new LivesPred().verbalizeIdeaAsSentence(renderer, lives1Value, lives1Args)));
            sb.append("]{").append(lives1);
        }
        sb.append("} but \\url[").append(new KnowsPred().verbalizeIdeaAsNpAlt(p1, p2));
        sb.append(escapeForURL(BeliefScale.verbalizeBeliefAsPredicateWithOnly(knowsValue)));
        sb.append("]{").append(knows).append("}, ");
        double maxVal = knowsValue + 1 - (contextAtom.equals(lives1) ? lives2Value : lives1Value);
        if (maxVal > 1 - RuleAtomGraph.DISSATISFACTION_PRECISION) {
            sb.append(contextAtom.equals(lives1) ? p1 : p2).append("'s address ");
            sb.append(" doesn't actually have an influence here.");
        } else {
            sb.append(" it should be unlikely that ").append(contextAtom.equals(lives1) ? p1 : p2);
            sb.append(" lives at the same place.");
        }
        return sb.toString();
    }

}

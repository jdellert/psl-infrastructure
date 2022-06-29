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

import de.tuebingen.sfs.psl.talk.BeliefScale;
import de.tuebingen.sfs.psl.talk.ConstantRenderer;
import de.tuebingen.sfs.psl.talk.pred.TalkingPredicate;

public class KnowsPred extends TalkingPredicate {

    public static final String NAME = "Knows";

    public KnowsPred() {
        super(NAME, 2); // arity = 2
    }

    @Override
    public String verbalizeIdeaWithBelief(ConstantRenderer renderer, double belief, String[] args) {
        return args[0] + " " + BeliefScale.verbalizeBeliefAsAdverb(belief) + " knows " + args[1];
    }

    @Override
    public String verbalizeIdeaAsSentence(ConstantRenderer renderer, String... args) {
        return args[0] + " knows in " + args[1];
    }

    @Override
    public String verbalizeIdeaAsNP(ConstantRenderer renderer, String... args) {
        return args[0] + " knowing " + args[1];
    }

    public String verbalizeIdeaAsNpAlt(String... args) {
        return "an acquaintance between " + args[0] + " and " + args[1];
    }
}

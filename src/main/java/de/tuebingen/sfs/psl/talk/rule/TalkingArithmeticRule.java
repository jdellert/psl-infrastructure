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
package de.tuebingen.sfs.psl.talk.rule;

import de.tuebingen.sfs.psl.engine.PslProblem;
import org.linqs.psl.model.rule.Rule;

public class TalkingArithmeticRule extends TalkingArithmeticRuleOrConstraint implements TalkingRule {

    // The weight arguments are here to enforce distinguishing between weighted rules and unweighted constraints.

    public TalkingArithmeticRule(String name, double weight, String ruleString, PslProblem pslProblem) {
        super(name, weight + ": " + ruleString, pslProblem);
    }

    public TalkingArithmeticRule(String name, double weight, String ruleString, PslProblem pslProblem,
                                 String verbalization) {
        super(name, weight + ": " + ruleString, pslProblem, verbalization);
    }

    public TalkingArithmeticRule(String name, double weight, String ruleString, Rule rule, PslProblem pslProblem) {
        super(name, weight + ": " + ruleString, rule, pslProblem);
    }

    public TalkingArithmeticRule(String name, double weight, String ruleString, Rule rule, PslProblem pslProblem,
                                 String verbalization) {
        super(name, weight + ": " + ruleString, rule, pslProblem, verbalization);
    }

    public TalkingArithmeticRule(String name, double weight, String ruleString) {
        super(name, weight + ": " + ruleString);
    }

    public TalkingArithmeticRule(String name, double weight, String ruleString, String verbalization) {
        super(name, weight + ": " + ruleString, verbalization);
    }

    public TalkingArithmeticRule(String serializedParameters) {
        super(serializedParameters);
    }

}

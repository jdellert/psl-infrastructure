package de.tuebingen.sfs.psl.talk;

import de.tuebingen.sfs.psl.engine.PslProblem;
import org.linqs.psl.model.rule.Rule;

public class TalkingArithmeticConstraint extends TalkingArithmeticRuleOrConstraint implements TalkingConstraint {
    public TalkingArithmeticConstraint(String name, String ruleString, PslProblem pslProblem) {
        super(name, ruleString.strip().endsWith(".") ? ruleString : ruleString + ".", pslProblem);
    }

    public TalkingArithmeticConstraint(String name, String ruleString, PslProblem pslProblem, String verbalization) {
        super(name, ruleString.strip().endsWith(".") ? ruleString : ruleString + ".", pslProblem, verbalization);
    }

    public TalkingArithmeticConstraint(String name, String ruleString, Rule rule, PslProblem pslProblem) {
        super(name, ruleString.strip().endsWith(".") ? ruleString : ruleString + ".", rule, pslProblem);
    }

    public TalkingArithmeticConstraint(String name, String ruleString, Rule rule, PslProblem pslProblem,
                                       String verbalization) {
        super(name, ruleString.strip().endsWith(".") ? ruleString : ruleString + ".", rule, pslProblem, verbalization);
    }

    public TalkingArithmeticConstraint(String name, String ruleString) {
        super(name, ruleString.strip().endsWith(".") ? ruleString : ruleString + ".");
    }

    public TalkingArithmeticConstraint(String name, String ruleString, String verbalization) {
        super(name, ruleString.strip().endsWith(".") ? ruleString : ruleString + ".", verbalization);
    }

    public TalkingArithmeticConstraint(String serializedParameters) {
        super(serializedParameters);
    }
}

package de.tuebingen.sfs.psl.talk;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.engine.RuleAtomGraph;
import de.tuebingen.sfs.psl.talk.pred.NotEqualPred;
import de.tuebingen.sfs.psl.util.data.StringUtils;
import de.tuebingen.sfs.psl.util.data.Tuple;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.AbstractArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.reasoner.function.FunctionComparator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TalkingArithmeticRule extends TalkingRule {

    // sum[i] == super.args[i] is a SummationAtom
    private boolean[] sum = null;
    // Is this an equative rule?
    private boolean equative = false;

    public TalkingArithmeticRule(String name, String ruleString, PslProblem pslProblem) {
        this(name, ruleString, createRule(pslProblem.getDataStore(), ruleString), pslProblem, null);
    }

    public TalkingArithmeticRule(String name, String ruleString, PslProblem pslProblem, String verbalization) {
        this(name, ruleString, createRule(pslProblem.getDataStore(), ruleString), pslProblem, verbalization);
    }

    public TalkingArithmeticRule(String name, String ruleString, Rule rule, PslProblem pslProblem) {
        this(name, ruleString, rule, pslProblem, null);
    }

    public TalkingArithmeticRule(String name, String ruleString, Rule rule, PslProblem pslProblem,
                                 String verbalization) {
        super(name, ruleString, rule, pslProblem, verbalization);
        if (rule instanceof AbstractArithmeticRule) {
            AbstractArithmeticRule ariRule = (AbstractArithmeticRule) rule;
            FunctionComparator comp = ariRule.getExpression().getComparator();
            equative = comp.equals(FunctionComparator.EQ);

            List<SummationAtomOrAtom> atoms = ariRule.getExpression().getAtoms();
            sum = new boolean[atoms.size()];
            for (int i = 0; i < sum.length; i++)
                sum[i] = atoms.get(i) instanceof SummationAtom;
        }
    }

    // For serialization.
    public TalkingArithmeticRule(String name, String ruleString) {
        super(name, ruleString);
    }

    // For serialization.
    public TalkingArithmeticRule(String name, String ruleString, String verbalization) {
        super(name, ruleString, verbalization);
    }

    // For serialization.
    // Override me!
    public TalkingArithmeticRule(String serializedParameters) {
        super("", "");
        String[] parameters = serializedParameters.split("-");
        setName(parameters[0]);
        setRuleString(parameters[1]);
        if (parameters.length > 2)
            setVerbalization(parameters[2]);
    }

    // Override me! Can just return "" if your talking rule doesn't need serialized parameters.
    @Override
    public String getSerializedParameters() {
        return getName() + "-" + getRuleString() + (getVerbalization() != null ? "-" + getVerbalization() : "");
    }

    public String getDefaultExplanation(String groundingName, String contextAtom,
                                        RuleAtomGraph rag, boolean whyExplanation) {
        return getDefaultExplanation(null, groundingName, contextAtom, rag, whyExplanation);
    }

    @Override
    public String getDefaultExplanation(ConstantRenderer renderer, String groundingName, String contextAtom,
                                        RuleAtomGraph rag, boolean whyExplanation) {
        List<Tuple> atomToStatus = rag.getLinkedAtomsForGroundingWithLinkStatusAsList(groundingName);
        if (equative) {
            StringBuilder sb = new StringBuilder();
            List<String> printableArgs = new ArrayList<>();
            for (Tuple tuple : atomToStatus) {
                String atom = tuple.get(0);
                if (!atom.equals(contextAtom))
                    printableArgs.add(atom);
            }
            if (printableArgs.size() == 0)
                sb.append(contextAtom).append(" has no competitors");
            else {
                sb.append(contextAtom).append(" competes with ");
                appendAnd(printableArgs, sb);
            }
            return sb.append(" in rule '").append(getName()).append("'.").toString();
        }

        int positiveArgs = 0;
        List<String> printableArgs = new ArrayList<>();
        List<Double> printableBeliefValues = new ArrayList<>();
        List<String[]> printablePredicateArgs = new ArrayList<>();
        Map<String, TalkingPredicate> nameToTalkingPredicate = getTalkingPredicates();
        List<TalkingPredicate> printableTalkingPredicates = new ArrayList<>();
        boolean contextFound = false;
        boolean contextPositive = false;
        for (Tuple tuple : atomToStatus) {
            String atom = tuple.get(0);
            if (atom.equals(contextAtom)) {
                contextFound = true;
                contextPositive = tuple.get(1).equals("+");
            } else if (atom.startsWith(NotEqualPred.SYMBOL)) {
                continue;
            } else if (tuple.get(1).equals("+")) {
                printableArgs.add(positiveArgs, atom);
                printableBeliefValues.add(positiveArgs, rag.getValue(atom));
                positiveArgs++;
            } else {
                printableArgs.add(atom);
                printableBeliefValues.add(rag.getValue(atom));
            }
            String[] predDetails = StringUtils.split(atom, '(');
            printableTalkingPredicates.add(nameToTalkingPredicate.get(predDetails[0]));
            printablePredicateArgs.add(StringUtils.split(predDetails[1].substring(0, predDetails[1].length() - 1), ","));
        }

        return getUnequativeExplanation(contextAtom, rag.getValue(contextAtom), contextFound, contextPositive,
                printableArgs, printableTalkingPredicates, printablePredicateArgs, printableBeliefValues,
                positiveArgs, true, whyExplanation, renderer);
    }

}

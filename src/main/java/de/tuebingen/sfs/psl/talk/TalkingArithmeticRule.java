package de.tuebingen.sfs.psl.talk;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.engine.RuleAtomGraph;
import de.tuebingen.sfs.psl.util.data.Tuple;

import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.AbstractArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.reasoner.function.FunctionComparator;

import java.util.ArrayList;
import java.util.List;

public class TalkingArithmeticRule extends TalkingRule {

	// sum[i] == super.args[i] is a SummationAtom
	private boolean[] sum = null;
	// Is this an equative rule?
	private boolean equative = false;
	
	public TalkingArithmeticRule() {
		// For serialization.
	}

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
			equative = comp.equals(FunctionComparator.Equality);

			List<SummationAtomOrAtom> atoms = ariRule.getExpression().getAtoms();
			sum = new boolean[atoms.size()];
			for (int i = 0; i < sum.length; i++)
				sum[i] = atoms.get(i) instanceof SummationAtom;
		}
	}

	@Override
	public String getDefaultExplanation(String groundingName, String contextAtom, RuleAtomGraph rag,
			boolean whyExplanation) {
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
		} else {
			int positiveArgs = 0;
			List<String> printableArgs = new ArrayList<>();
			boolean contextFound = false;
			boolean contextPositive = false;
			for (Tuple tuple : atomToStatus) {
                String atom = tuple.get(0);
                if (atom.equals(contextAtom)) {
                    contextFound = true;
                    contextPositive = tuple.get(1).equals("+");
                } else if (tuple.get(1).equals("+")) {
                    printableArgs.add(positiveArgs, atom);
                    positiveArgs++;
                } else
                    printableArgs.add(atom);
			}

			return getUnequativeExplanation(contextAtom, rag.getValue(contextAtom), contextFound, contextPositive,
					printableArgs, null, positiveArgs, true, whyExplanation);
		}
	}

}

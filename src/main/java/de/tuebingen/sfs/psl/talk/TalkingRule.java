package de.tuebingen.sfs.psl.talk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.formula.FormulaAnalysis;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.AbstractArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.logical.AbstractLogicalRule;
import org.linqs.psl.parser.ModelLoader;
import org.linqs.psl.parser.RulePartial;
import org.linqs.psl.reasoner.function.FunctionComparator;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.engine.RuleAtomGraph;
import de.tuebingen.sfs.psl.util.data.Tuple;

public abstract class TalkingRule {

	public static TalkingRule createTalkingRule(String name, String ruleString, Rule rule, PslProblem pslProblem) {
		return createTalkingRule(name, ruleString, rule, pslProblem, null);
	}

	public static TalkingRule createTalkingRule(String name, String ruleString, Rule rule, PslProblem pslProblem,
			String verbalization) {
		if (rule instanceof AbstractLogicalRule)
			return new TalkingLogicalRule(name, ruleString, rule, pslProblem, verbalization);
		if (rule instanceof AbstractArithmeticRule)
			return new TalkingArithmeticRule(name, ruleString, rule, pslProblem, verbalization);

		return null;
	}

	// Rule name
	private String name;
	// String representation of rule
	private String ruleString;
	// The rule itself
	private Rule rule;
	// A VERBALIZATION of the rule that can be presented to the user.
	private String verbalization;
	// Arguments of the rule as entered in the rule string
	// (e.g. "Prec(Lang1, Lang2, Proto)")
	private String[] args;
	private PslProblem pslProblem;
	
	TalkingRule(){
		// For serialization.
	}
	
	TalkingRule(String name, String ruleString, Rule rule, PslProblem pslProblem, String verbalization) {
		this.name = name;
		this.ruleString = ruleString;
		this.rule = rule;
		if (verbalization == null) {
			this.verbalization = name;
		} else {
			this.verbalization = verbalization;
		}

		List<SummationAtomOrAtom> atoms;
		if (rule instanceof AbstractLogicalRule) {
			AbstractLogicalRule logRule = (AbstractLogicalRule) rule;
			FormulaAnalysis.DNFClause dnf = logRule.getDNF();
			atoms = new ArrayList<>(dnf.getPosLiterals());
			atoms.addAll(dnf.getNegLiterals());
		} else if (rule instanceof AbstractArithmeticRule) {
			AbstractArithmeticRule ariRule = (AbstractArithmeticRule) rule;
			FunctionComparator comparator = ariRule.getExpression().getComparator();
			atoms = ariRule.getExpression().getAtoms();
		} else {
			atoms = new ArrayList<>();
		}

		args = new String[atoms.size()];
		for (int i = 0; i < atoms.size(); i++) {
			SummationAtomOrAtom atom = atoms.get(i);
			Predicate pred = (atom instanceof SummationAtom) ? ((SummationAtom) atom).getPredicate()
					: ((Atom) atom).getPredicate();
			args[i] = atom.toString();
		}

		this.pslProblem = pslProblem;
	}

	public static Rule createRule(DataStore dataStore, String ruleString) {
		Rule rule = null;
		try {
			RulePartial partial = ModelLoader.loadRulePartial(dataStore, ruleString);
			rule = partial.toRule();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return rule;
	}

	public String getName() {
		return name;
	}

	public String getRuleString() {
		return ruleString;
	}

	public Rule getRule() {
		return rule;
	}

	public String getVerbalization() {
		return verbalization;
	}

	protected String[] getArgs() {
		return args;
	}

	protected PslProblem getPslProblem() {
		return pslProblem;
	}

	public String generateExplanation(String groundingName, String contextAtom, RuleAtomGraph rag,
			boolean whyExplanation) {
		return getDefaultExplanation(groundingName, contextAtom, rag, whyExplanation);
	}

	public abstract String getDefaultExplanation(String groundingName, String contextAtom, RuleAtomGraph rag,
			boolean whyExplanation);

	// TODO delete, update method calls
	String getUnequativeExplanation(String contextAtom, double belief, boolean contextFound, boolean contextPositive,
			List<String> printableArgs, List<Double> printableBeliefValues, int positiveArgs, boolean directFormulation,
			boolean whyExplanation) {
		return getUnequativeExplanation(contextAtom, belief, contextFound, contextPositive, printableArgs, null, null,
				printableBeliefValues, positiveArgs, directFormulation, whyExplanation);
	}

	/**
	 * Generate explanation for logical rules and for non-equative arithmetic
	 * rules.
	 * 
	 * @param contextAtom
	 *            Atom displayed in the fact window
	 * @param belief
	 *            Value of context atom
	 * @param contextFound
	 *            Was the context found amongst the arguments of the ground
	 *            rule? If not, its predicate is closed or it is on the RAG's
	 *            renderer's ignore list.
	 * @param contextPositive
	 *            Is the context atom a positive literal?
	 * @param printableArgs
	 *            All arguments of the ground rule that are eligible for
	 *            printing (i.e. open and unequal to the context atom)
	 * @param printableBeliefValues
	 *            The belief values corresponding to the entries in
	 *            printableArgs.
	 * @param positiveArgs
	 *            The first [positiveArgs] entries of printableArgs are positive
	 *            literals, the rest is negative
	 * @param directFormulation
	 *            Use direct formulation? (Else contrafactive)
	 * @param whyExplanation
	 *            If true: explanation appears in the WHY block, otherwise: in
	 *            the WHY NOT block.
	 * @return Unequative explanation
	 */
	String getUnequativeExplanation(String contextAtom, double belief, boolean contextFound, boolean contextPositive,
			List<String> printableArgs, List<TalkingPredicate> printableTalkingPredicates,
			List<String[]> printablePredicateArgs, List<Double> printableBeliefValues, int positiveArgs,
			boolean directFormulation, boolean whyExplanation) {
		StringBuilder sb = new StringBuilder();

		// Context atom is not amongst given ground atoms
		// (i.e. is closed or on ignore list)
		if (!contextFound) {
			if (printableArgs.size() > 0) {
				sb.append("Rule '").append(getVerbalization()).append("' links ").append(contextAtom).append(" to ");
				appendAnd(printableArgs, sb);
			} else
				sb.append("Rule '").append(getVerbalization()).append("' includes ").append(contextAtom);
			sb.append(" with unknown effects.");
			return sb.toString();
		}

		int negativeArgs = printableArgs.size() - positiveArgs;

		sb.append("This is ");
		if (whyExplanation)
			sb.append("supported");
		else
			sb.append("limited");

		if (printableArgs.size() == 0)
			return sb.append(" by the rule '").append(getVerbalization()).append("'").toString();

		if (directFormulation)
			sb.append(" by ");
		else
			sb.append(" because ");

		if (!directFormulation) {
			sb.append("Otherwise, ");
			if (positiveArgs > 1)
				sb.append("one of ");
		}

		if (positiveArgs > 0) {
			if (directFormulation) {
				List<String> components = new ArrayList<String>();
				for (int i = 0; i < positiveArgs; i++) {
					StringBuilder sbComponent = new StringBuilder();
//					System.out.println(name + ": " + ruleString);
					addURL(printableArgs, printableTalkingPredicates, printablePredicateArgs, i, sbComponent);
					sbComponent.append(" reaching a ");
					if (printableTalkingPredicates != null
							&& printableTalkingPredicates.get(i).verbalizeOnHighLowScale()) {
						sbComponent.append("similarity");
					} else {
						sbComponent.append("confidence");
					}
					sbComponent.append(" level of \\textit{");
					if (printableTalkingPredicates != null
							&& printableTalkingPredicates.get(i).verbalizeOnHighLowScale()) {
						sbComponent.append(BeliefScale.verbalizeBeliefAsAdjectiveHigh(printableBeliefValues.get(i)));
					} else if (printableBeliefValues != null) {
						sbComponent.append(BeliefScale.verbalizeBeliefAsAdjective(printableBeliefValues.get(i)));
					} // TODO else? (arithmetic rules)
					sbComponent.append("}");
					components.add(sbComponent.toString());
				}
				appendAnd(components, sb, false);
				if (negativeArgs > 0)
					sb.append(", and ");
			} else {
				if (printableTalkingPredicates == null)
					appendAnd(printableArgs, 0, positiveArgs, sb, true);
				else
					appendAnd(printableArgs, printableTalkingPredicates, printablePredicateArgs, 0, positiveArgs, sb,
							true);
				sb.append(" would have to be ").append((contextPositive) ? "less" : "more").append(" likely");
				// TODO scales: likely vs. high
				if (negativeArgs > 0)
					sb.append(", or ");
				if (negativeArgs > 1)
					sb.append("one of ");
			}
		}

		if (negativeArgs > 0) {
			if (directFormulation) {
				List<String> components = new ArrayList<String>();
				for (int i = positiveArgs; i < printableArgs.size(); i++) {
					StringBuilder sbComponent = new StringBuilder();
					addURL(printableArgs, printableTalkingPredicates, printablePredicateArgs, i, sbComponent);
					sbComponent.append(" being only ");
					if (printableTalkingPredicates != null
							&& printableTalkingPredicates.get(i).verbalizeOnHighLowScale()) {
						sbComponent.append(BeliefScale.verbalizeBeliefAsAdjectiveHigh(printableBeliefValues.get(i)));
					} else if (printableBeliefValues != null) {
						sbComponent.append(BeliefScale.verbalizeBeliefAsAdjective(printableBeliefValues.get(i)));
					} // TODO else? (arithmetic rules)
					components.add(sbComponent.toString());
				}
				appendAnd(components, sb, false);

			} else {

				if (printableTalkingPredicates == null)
					appendAnd(printableArgs, positiveArgs, printableArgs.size(), sb, true);
				else
					appendAnd(printableArgs, printableTalkingPredicates, printablePredicateArgs, positiveArgs,
							printableArgs.size(), sb, true);
				sb.append(" would have to be ").append((contextPositive) ? "more" : "less").append(" likely");
				// TODO scales: likely vs. high
			}
		}

		sb.append(". (Rule: ").append(getVerbalization()).append(")");

		// System.out.println(sb.toString());

		return sb.toString();
	}

	public static void appendAnd(List<String> args, StringBuilder sb) {
		appendAnd(args, sb, true);
	}

	public static void appendAnd(List<String> args, StringBuilder sb, boolean url) {
		appendAnd(args, 0, args.size(), sb, url);
	}

	public static void appendAnd(List<String> args, int from, int to, StringBuilder sb, boolean url) {
		appendList(args, from, to, "and", sb, url);
	}

	public static void appendAnd(List<String> printableArgs, List<TalkingPredicate> printableTalkingPredicates,
			List<String[]> printablePredicateArgs, int from, int to, StringBuilder sb, boolean url) {
		appendList(printableArgs, printableTalkingPredicates, printablePredicateArgs, from, to, "and", sb, url);
	}

	public static void appendOr(List<String> args, StringBuilder sb) {
		appendOr(args, sb, true);
	}

	public static void appendOr(List<String> args, StringBuilder sb, boolean url) {
		appendOr(args, 0, args.size(), sb, url);
	}

	public static void appendOr(List<String> args, int from, int to, StringBuilder sb, boolean url) {
		appendList(args, from, to, "or", sb, url);
	}

	public static void appendOr(List<String> printableArgs, List<TalkingPredicate> printableTalkingPredicates,
			List<String[]> printablePredicateArgs, int from, int to, StringBuilder sb, boolean url) {
		appendList(printableArgs, printableTalkingPredicates, printablePredicateArgs, from, to, "or", sb, url);
	}

	private static void appendList(List<String> args, int from, int to, String conj, StringBuilder sb, boolean url) {
		for (int i = from; i < to; i++) {
			if (url)
				sb.append("\\url{");
			sb.append(args.get(i));
			if (url)
				sb.append("}");
			if (i == args.size() - 2)
				sb.append(" ").append(conj).append(" ");
			else if (i != args.size() - 1)
				sb.append(", ");
		}
	}

	private static void appendList(List<String> printableArgs, List<TalkingPredicate> printableTalkingPredicates,
			List<String[]> printablePredicateArgs, int from, int to, String conj, StringBuilder sb, boolean url) {
		for (int i = from; i < to; i++) {
			if (url)
				addURL(printableArgs, printableTalkingPredicates, printablePredicateArgs, i, sb);
			else
				sb.append(printableArgs.get(i));
			if (i == printableArgs.size() - 2)
				sb.append(" ").append(conj).append(" ");
			else if (i != printableArgs.size() - 1)
				sb.append(", ");
		}
	}

	private static void addURL(List<String> printableArgs, List<TalkingPredicate> printableTalkingPredicates,
			List<String[]> printablePredicateArgs, int index, StringBuilder sb) {
		String predicateName = printableArgs.get(index);
		sb.append("\\url");
		if (printableTalkingPredicates != null && printablePredicateArgs != null) {
//			System.out.println(index);
//			for (int i = 0; i < printablePredicateArgs.size(); i++){
//				System.out.println(printableTalkingPredicates.get(i) + " - " + Arrays.toString(printablePredicateArgs.get(i)));
//			}			
			String verbalization = printableTalkingPredicates.get(index)
					.verbalizeIdeaAsNP(printablePredicateArgs.get(index));
			if (!predicateName.equals(verbalization)) {
				sb.append("[the ");
				sb.append(escapeForURL(verbalization));
				sb.append("]");
			}
		}
		sb.append("{").append(predicateName).append("}");
	}

	protected static String escapeForURL(String s) {
		s = s.replaceAll("(?!\\\\)\\[", "\\\\[");
		s = s.replaceAll("(?!\\\\)\\]", "\\\\]");
		s = s.replaceAll("(?!\\\\)\\{", "\\\\{");
		s = s.replaceAll("(?!\\\\)\\}", "\\\\}");
		return s;
	}

	@SuppressWarnings("rawtypes")
	protected Map[] getComponents(String groundingName, RuleAtomGraph rag) {
		String[] args = getArgs();
		// Get arguments of ground rule
		List<Tuple> atomToStatus = rag.getLinkedAtomsForGroundingWithLinkStatusAsList(groundingName);

		HashMap<String, String> atoms = new HashMap<>();
		HashMap<String, Double> beliefValues = new HashMap<>();
		HashMap<String, String[]> arguments = new HashMap<>();

		for (int i = 0; i < args.length; i++) {
			String groundAtom = atomToStatus.get(i).get(0);
            String[] predDetails = groundAtom.split("\\(");
            String predName = predDetails[0];
            atoms.put(predName, groundAtom);
            beliefValues.put(predName, rag.getValue(groundAtom));
            arguments.put(predName, predDetails[1].substring(0, predDetails[1].length() - 1).split(","));
		}
		return new HashMap[] { atoms, beliefValues, arguments };
	}

	@SuppressWarnings("rawtypes")
	protected Map[] getDetailedComponentsInclClosed(String groundingName, RuleAtomGraph rag) {
		String[] args = getArgs();
		// Get arguments of ground rule
		List<Tuple> atomToStatus = rag.getLinkedAtomsForGroundingWithLinkStatusAsList(groundingName);

		HashMap<String, String> atomsLite = new HashMap<>();
		HashMap<String, Double> beliefValues = new HashMap<>();
		HashMap<String, Double> beliefValuesLite = new HashMap<>();
		HashMap<String, String[]> argumentsLite = new HashMap<>();

		for (int i = 0; i < args.length; i++) {
			String groundAtom = atomToStatus.get(i).get(0);
			beliefValues.put(groundAtom, rag.getValue(groundAtom));
			String[] predDetails = groundAtom.split("\\(");
			String predName = predDetails[0];
			atomsLite.put(predName, groundAtom);
			beliefValuesLite.put(predName, rag.getValue(groundAtom));
			argumentsLite.put(predName, predDetails[1].substring(0, predDetails[1].length() - 1).split(","));
		}
		return new HashMap[] { atomsLite, beliefValues, beliefValuesLite, argumentsLite };
	}

}

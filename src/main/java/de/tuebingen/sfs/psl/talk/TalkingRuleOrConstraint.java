package de.tuebingen.sfs.psl.talk;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.engine.RuleAtomGraph;
import de.tuebingen.sfs.psl.util.data.Tuple;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// To use a custom class for properly rendering the PSL constants (atom arguments), create the following classes:
// - `class YourConstantRenderer`, a class that assigns human-understandable strings to PSL constants
// - `interface YourTalkingRule` 
// - `class YourTalkingLogicalRule extends TalkingLogicalRule implements YourTalkingRule`
// - `class YourTalkingArithmeticRule extends TalkingArithmeticRule implements YourTalkingRule`
// - `class YourTalkingPredicate extends TalkingPredicate`
// `YourTalkingPredicate` and `YourTalkingRule` can extend their explanation/verbalization
// methods by an additional argument: YourConstantRenderer.
// An example implementation of this will be available at
// https://github.com/jdellert/etinen-shared/tree/master/src/main/java/de/tuebingen/sfs/eie/talk
public abstract class TalkingRuleOrConstraint {

    //	private static final Pattern ATOM_PATTERN = Pattern.compile("(?<=\\w\\()[^\\(]+(?=\\))");
    private static final Pattern ATOM_PATTERN = Pattern.compile("\\w{4,}\\([^\\(]+\\)");
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
    private PslProblem pslProblem = null;
    private Map<String, TalkingPredicate> talkingPreds = null;

    TalkingRuleOrConstraint(String name, String ruleString, Rule rule, PslProblem pslProblem, String verbalization) {
        this.name = name;
        setVerbalization(verbalization);
        setRule(ruleString, rule);
        this.pslProblem = pslProblem;
    }

    // For serialization.
    TalkingRuleOrConstraint(String name, String ruleString) {
        this(name, ruleString, null);
    }

    // For serialization.
    TalkingRuleOrConstraint(String name, String ruleString, String verbalization) {
        this.pslProblem = null;
        this.rule = null;
        setName(name);
        setVerbalization(verbalization);
        setRuleString(ruleString);
    }

    public static TalkingRuleOrConstraint createTalkingRuleOrConstraint(String name, String ruleString, Rule rule,
                                                                        PslProblem pslProblem) {
        return createTalkingRuleOrConstraint(name, ruleString, rule, pslProblem, null);
    }

    public static TalkingRuleOrConstraint createTalkingRuleOrConstraint(String name, String ruleString, Rule rule,
                                                                        PslProblem pslProblem, String verbalization) {
        if (TalkingRule.isWeightedRule(ruleString)) {
            if (rule instanceof AbstractLogicalRule)
                return new TalkingLogicalRule(name, ruleString, rule, pslProblem, verbalization);
            if (rule instanceof AbstractArithmeticRule)
                return new TalkingArithmeticRule(name, ruleString, rule, pslProblem, verbalization);
        }
        if (rule instanceof AbstractLogicalRule)
            return new TalkingLogicalConstraint(name, ruleString, rule, pslProblem, verbalization);
        if (rule instanceof AbstractArithmeticRule)
            return new TalkingArithmeticConstraint(name, ruleString, rule, pslProblem, verbalization);
        return null;
    }

    public static Rule createRule(DataStore dataStore, String ruleString) {
        Rule rule = null;
        try {
            RulePartial partial = ModelLoader.loadRulePartial(dataStore, ruleString);
            rule = partial.toRule();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rule;
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
                                 List<String[]> printablePredicateArgs, int from, int to, StringBuilder sb, boolean url,
                                 ConstantRenderer renderer) {
        appendList(printableArgs, printableTalkingPredicates, printablePredicateArgs, from, to, "and", sb, url,
                renderer);
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
                                List<String[]> printablePredicateArgs, int from, int to, StringBuilder sb, boolean url,
                                ConstantRenderer renderer) {
        appendList(printableArgs, printableTalkingPredicates, printablePredicateArgs, from, to, "or", sb, url,
                renderer);
    }

    private static void appendList(List<String> args, int from, int to, String conj, StringBuilder sb, boolean url) {
        for (int i = from; i < to; i++) {
            if (url) sb.append("\\url{");
            sb.append(args.get(i));
            if (url) sb.append("}");
            if (i == args.size() - 2) sb.append(" ").append(conj).append(" ");
            else if (i != args.size() - 1) sb.append(", ");
        }
    }

    private static void appendList(List<String> printableArgs, List<TalkingPredicate> printableTalkingPredicates,
                                   List<String[]> printablePredicateArgs, int from, int to, String conj,
                                   StringBuilder sb, boolean url, ConstantRenderer renderer) {
        for (int i = from; i < to; i++) {
            if (url) addURL(printableArgs, printableTalkingPredicates, printablePredicateArgs, i, sb, renderer);
            else sb.append(printableArgs.get(i));
            if (i == printableArgs.size() - 2) sb.append(" ").append(conj).append(" ");
            else if (i != printableArgs.size() - 1) sb.append(", ");
        }
    }

    private static void addURL(List<String> printableArgs, List<TalkingPredicate> printableTalkingPredicates,
                               List<String[]> printablePredicateArgs, int index, StringBuilder sb,
                               ConstantRenderer renderer) {
        String predicateName = printableArgs.get(index);
        sb.append("\\url");
        if (printableTalkingPredicates != null && printablePredicateArgs != null) {
            String verbalization = printableTalkingPredicates.get(index)
                    .verbalizeIdeaAsNP(renderer, printablePredicateArgs.get(index));
            if (!predicateName.equals(verbalization)) {
                sb.append("[");
                sb.append(escapeForURL(verbalization));
                sb.append("]");
            }
        }
        sb.append("{").append(predicateName).append("}");
    }

    private static void addSentenceWithURL(List<String> printableArgs,
                                           List<TalkingPredicate> printableTalkingPredicates,
                                           List<String[]> printablePredicateArgs, List<Double> printableBeliefValues,
                                           int index, StringBuilder sb, ConstantRenderer renderer) {
        String predicateName = printableArgs.get(index);
        sb.append("\\url");
        if (printableTalkingPredicates != null && printablePredicateArgs != null && printableBeliefValues != null) {
            // This requires having implemented subclasses of TalkingPredicate in order to actually look nice.
            String verbalization = printableTalkingPredicates.get(index)
                    .verbalizeIdeaAsSentence(renderer, printableBeliefValues.get(index),
                            printablePredicateArgs.get(index));
            if (!predicateName.equals(verbalization)) {
                sb.append("[");
                sb.append(escapeForURL(verbalization));
                sb.append("]");
            }
        }
        sb.append("{").append(predicateName).append("}");
    }

    public static String escapeForURL(String s) {
        s = s.replaceAll("(?!\\\\)\\[", "\\\\[");
        s = s.replaceAll("(?!\\\\)\\]", "\\\\]");
        s = s.replaceAll("(?!\\\\)\\{", "\\\\{");
        s = s.replaceAll("(?!\\\\)\\}", "\\\\}");
        return s;
    }

    protected void setRule(String ruleString, Rule rule) {
        this.rule = rule;
        this.ruleString = ruleString;

        if (rule != null) {
            List<SummationAtomOrAtom> atoms;
            if (rule instanceof AbstractLogicalRule) {
                AbstractLogicalRule logRule = (AbstractLogicalRule) rule;
                FormulaAnalysis.DNFClause dnf = logRule.getNegatedDNF();
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
                Predicate pred = (atom instanceof SummationAtom) ? ((SummationAtom) atom).getPredicate() :
                        ((Atom) atom).getPredicate();
                args[i] = atom.toString();
            }
            return;
        }

        List<String> args = new ArrayList<>();
        Matcher matcher = ATOM_PATTERN.matcher(ruleString);
        while (matcher.find()) {
            String argSection = matcher.group();
            for (String arg : argSection.split(",")) {
                args.add(arg.trim());
            }
        }
        this.args = new String[args.size()];
        for (int i = 0; i < args.size(); i++) {
            this.args[i] = args.get(i);
        }
    }

    public String getName() {
        return name;
    }

    protected void setName(String name) {
        this.name = name;
    }

    public String getRuleString() {
        return ruleString;
    }

    protected void setRuleString(String ruleString) {
        setRule(ruleString, null);
    }

    public Rule getRule() {
        return rule;
    }

    public String getVerbalization() {
        return verbalization;
    }

    protected void setVerbalization(String verbalization) {
        this.verbalization = (verbalization != null) ? verbalization : name;
    }

    protected String[] getArgs() {
        return args;
    }

    protected PslProblem getPslProblem() {
        return pslProblem;
    }

    protected Map<String, TalkingPredicate> getTalkingPredicates() {
        if (pslProblem != null) return pslProblem.getTalkingPredicates();
        return talkingPreds;
    }

    public void setTalkingPredicates(Map<String, TalkingPredicate> talkingPreds) {
        this.talkingPreds = talkingPreds;
    }

    public String generateExplanation(ConstantRenderer renderer, String groundingName, String contextAtom,
                                      RuleAtomGraph rag, boolean whyExplanation) {
        return getDefaultExplanation(renderer, groundingName, contextAtom, rag, whyExplanation);
    }

    public abstract String getDefaultExplanation(ConstantRenderer renderer, String groundingName, String contextAtom,
                                                 RuleAtomGraph rag, boolean whyExplanation);

    /**
     * @return An introductory sentence summarizing the reasoning pattern expressed by the rule.
     */
    public String getIntroductorySentence() {
        if (verbalization.isEmpty()) {
            return name + ".";
        }
        return verbalization;
    }

    /**
     * Returns the explanation for a rule with a context atom that's on the PSL problem's ignore list.
     *
     * @param contextAtom   Atom displayed in the fact window
     * @param printableArgs All arguments of the ground rule that are eligible for
     *                      printing (i.e. unequal to the context atom and not on the ignore list)
     * @return the verbalization
     */
    public String getContextlessExplanation(String contextAtom, List<String> printableArgs) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIntroductorySentence()).append(" This rule ");
        if (printableArgs.size() > 0) {
            sb.append("links ").append(contextAtom).append(" to ");
            appendAnd(printableArgs, sb); // method adds atom URLs
        } else {
            sb.append("includes ").append(contextAtom);
        }
        sb.append(" with unknown effects. (").append(name).append(")");
        return sb.toString();
    }

    /**
     * Returns the explanation for a rule with a context atom that cannot be pushed further in the direction the rule
     * exerts pressure.
     *
     * @param printableArgs              All arguments of the ground rule that are eligible for printing (i.e. open and unequal to the context atom)
     * @param printableTalkingPredicates
     * @param printablePredicateArgs
     * @param printableBeliefValues      The belief values corresponding to the entries in printableArgs.
     * @param renderer                   Renderer for the atom arguments. Can be null.
     * @return
     */
    public String getExplanationForPolarAtom(List<String> printableArgs,
                                             List<TalkingPredicate> printableTalkingPredicates,
                                             List<String[]> printablePredicateArgs, List<Double> printableBeliefValues,
                                             ConstantRenderer renderer) {
        // If the context atom is 1.0/0.0 and can't be higher/lower:
        // Intro, then, in parentheses, list the atom links/values.
        return getMinimalExplanation(printableArgs, printableTalkingPredicates, printablePredicateArgs,
                printableBeliefValues, renderer);
    }

    /**
     * Returns a very minimal explanation consisting of an introductory sentence followed by a list of links to
     * conntected atoms (when possible, verbalized) and their belief values.
     *
     * @param printableArgs              All arguments of the ground rule that are eligible for printing (i.e. open and unequal to the context atom)
     * @param printableTalkingPredicates
     * @param printablePredicateArgs
     * @param printableBeliefValues      The belief values corresponding to the entries in printableArgs.
     * @param renderer                   Renderer for the atom arguments. Can be null.
     * @return
     */
    public String getMinimalExplanation(List<String> printableArgs, List<TalkingPredicate> printableTalkingPredicates,
                                        List<String[]> printablePredicateArgs, List<Double> printableBeliefValues,
                                        ConstantRenderer renderer) {
        // Intro, then, in parentheses, list the atom links/values.
        StringBuilder sb = new StringBuilder();
        sb.append(getIntroductorySentence()).append(" (");

        int numArgs = printableArgs.size();
        for (int i = 0; i < numArgs; i++) {
            addSentenceWithURL(printableArgs, printableTalkingPredicates, printablePredicateArgs, printableBeliefValues,
                    i, sb, renderer);
            if (i == numArgs - 2) {
                sb.append("and ");
            } else if (i != numArgs - 1) {
                sb.append(", ");
            }
        }
        sb.append(".)");
        return sb.toString();
    }

    /**
     * Returns an explanation for the consequent of a logical rule (A & B -> *C*) or
     * for the (single) atom that opposes the other atoms in an arithmetic rule expressing an inequality (A + B <= *C*).
     *
     * @param printableArgs
     * @param printableTalkingPredicates
     * @param printablePredicateArgs
     * @param printableBeliefValues
     * @param renderer
     * @param whyNotLower
     * @param consequent
     * @param consequentArgs
     * @param consequentPredicate
     * @return the verbalization
     */
    public String getExplanationForConsequent(List<String> printableArgs,
                                              List<TalkingPredicate> printableTalkingPredicates,
                                              List<String[]> printablePredicateArgs, List<Double> printableBeliefValues,
                                              ConstantRenderer renderer, boolean whyNotLower, String consequent,
                                              String[] consequentArgs, TalkingPredicate consequentPredicate) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIntroductorySentence()).append(" ");
        sb.append("Since ");
        int numArgs = printableArgs.size();
        for (int i = 0; i < numArgs; i++) {
            addSentenceWithURL(printableArgs, printableTalkingPredicates, printablePredicateArgs, printableBeliefValues,
                    i, sb, renderer);
            if (i == numArgs - 1) {
                sb.append("and ");
            } else {
                sb.append(", ");
            }
        }
        sb.append("the value of ");
        if (consequentPredicate == null) {
            sb.append(consequent);
        } else {
            sb.append(consequentPredicate.verbalizeIdeaAsNP(renderer, consequentArgs));
        }
        sb.append(" has a");
        if (whyNotLower) {
            sb.append(" lower");
        } else {
            sb.append("n upper");
        }
        sb.append(" limit.");
        // TODO Tie this to the RuleAtomGraph's computeBodyScore method
        // and the minimum/maximum methods in the BeliefScale class instead.
        // -> "The atom needs to be at least X.

        return sb.toString();
    }

    /**
     * Returns the explanation for a context atom in a rule that is trivially satisfied by the consequent's value.
     * If the context atom is in the consequent of the rule, use the method {@link #getExplanationForConsequent(
     *List, List, List, List, ConstantRenderer, boolean, String, String[], TalkingPredicate)} instead.
     *
     * @param contextAtom                Atom displayed in the fact window.
     * @param printableArgs
     * @param printableTalkingPredicates
     * @param printablePredicateArgs
     * @param printableBeliefValues
     * @param renderer
     * @param whyNotLower
     * @param consequent                 The atom in the consequent of the rule. NOT identical to the context atom!
     * @param consequentArgs
     * @param consequentPredicate
     * @return the verbalization
     */
    public String getExplanationForTriviallySatisfiedRule(String contextAtom, List<String> printableArgs,
                                                          List<TalkingPredicate> printableTalkingPredicates,
                                                          List<String[]> printablePredicateArgs,
                                                          List<Double> printableBeliefValues, ConstantRenderer renderer,
                                                          boolean whyNotLower, String consequent,
                                                          String[] consequentArgs,
                                                          TalkingPredicate consequentPredicate) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIntroductorySentence()).append(" ");
        sb.append("In this case, ");
        int numArgs = printableArgs.size();
        for (int i = 0; i < numArgs; i++) {
            // TODO no link if this is the contextAtom
            addSentenceWithURL(printableArgs, printableTalkingPredicates, printablePredicateArgs, printableBeliefValues,
                    i, sb, renderer);
            if (i == numArgs - 1) {
                sb.append("and ");
            } else {
                sb.append(", ");
            }
        }

        sb.append("determine a ");
        if (whyNotLower) {
            sb.append("minimum");
        } else {
            sb.append("maximum");
        }
        sb.append(" value for ");
        if (consequentPredicate == null) {
            sb.append(consequent);
        } else {
            sb.append(consequentPredicate.verbalizeIdeaAsNP(renderer, consequentArgs));
        }
        sb.append(". However, since it already has a value of ");
        if (whyNotLower) {
            sb.append("0 %");
        } else {
            sb.append("100 %");
        }
        sb.append(", changing the value of any of the other atoms would not violate this rule");
        return sb.toString();
    }

    String getUnequativeExplanation(String contextAtom, double belief, boolean contextFound, boolean contextPositive,
                                    List<String> printableArgs, List<TalkingPredicate> printableTalkingPredicates,
                                    List<String[]> printablePredicateArgs, List<Double> printableBeliefValues,
                                    int positiveArgs, boolean directFormulation, boolean whyNotLower,
                                    ConstantRenderer renderer) {
        // TODO get rid of this method and update the other methods accordingly
        return getUnequativeExplanation(contextAtom, belief, contextFound, contextPositive, printableArgs,
                printableTalkingPredicates, printablePredicateArgs, printableBeliefValues, positiveArgs,
                directFormulation, whyNotLower, renderer, null, false, -1, null, null);
    }

    /**
     * Generate explanation for logical rules and for non-equative arithmetic
     * rules.
     *
     * @param contextAtom                Atom displayed in the fact window
     * @param belief                     Value of context atom
     * @param contextFound               Was the context found amongst the arguments of the ground
     *                                   rule? If not, its predicate is closed or it is on the RAG's
     *                                   renderer's ignore list.
     * @param contextPositive            Is the context atom a positive literal?
     * @param printableArgs              All arguments of the ground rule that are eligible for
     *                                   printing (i.e. open and unequal to the context atom)
     * @param printableTalkingPredicates The talking predicates corresponding to the printableArgs.
     * @param printablePredicateArgs     The belief values corresponding to the printableArgs.
     * @param printableBeliefValues      The belief values corresponding to the entries in
     *                                   printableArgs.
     * @param positiveArgs               The first [positiveArgs] entries of printableArgs are positive
     *                                   literals, the rest is negative
     * @param directFormulation          Use direct formulation? (Else contrafactive)
     * @param whyNotLower                If true: explanation appears in the WHY block, otherwise: in
     *                                   the WHY NOT block.
     * @param renderer                   Renderer for the atom arguments. Can be null.
     * @param consequent                 The atom in the consequent of the rule. Can be null.
     * @param negatedConsequent          True if consequent is negated. Only matters if consequent != null.
     * @param consequentBelief           The belief value of the consequent. Only matters if consequent != null.
     * @param consequentPredicate        The predicate to which the consequent belongs. Can be null even if the consequent is non-null.
     * @return Unequative explanation
     */
    String getUnequativeExplanation(String contextAtom, double belief, boolean contextFound, boolean contextPositive,
                                    List<String> printableArgs, List<TalkingPredicate> printableTalkingPredicates,
                                    List<String[]> printablePredicateArgs, List<Double> printableBeliefValues,
                                    int positiveArgs, boolean directFormulation, boolean whyNotLower,
                                    ConstantRenderer renderer, String consequent, boolean negatedConsequent,
                                    double consequentBelief, String[] consequentArgs,
                                    TalkingPredicate consequentPredicate) {
        if (!contextFound) {
            // Context atom is not among given ground atoms (e.g. because it's on the problem's ignore list)
            return getContextlessExplanation(contextAtom, printableArgs);
        }

        if ((belief > 1 - RuleAtomGraph.DISSATISFACTION_PRECISION && !whyNotLower) ||
                (belief < RuleAtomGraph.DISSATISFACTION_PRECISION && whyNotLower)) {
            // "Why is this atom with value 1.0 not higher?" or
            // "Why is this atom with value 0.0 not lower?"
            return getExplanationForPolarAtom(printableArgs, printableTalkingPredicates, printablePredicateArgs,
                    printableBeliefValues, renderer);
        }

        if (contextAtom.equals(consequent)) {
            return getExplanationForConsequent(printableArgs, printableTalkingPredicates, printablePredicateArgs,
                    printableBeliefValues, renderer, whyNotLower, consequent, consequentArgs, consequentPredicate);
        }

        if (consequent != null && ((negatedConsequent && consequentBelief < RuleAtomGraph.DISSATISFACTION_PRECISION) ||
                (!negatedConsequent && consequentBelief > 1 - RuleAtomGraph.DISSATISFACTION_PRECISION))) {
            // The rule is trivially satisfied.
            return getExplanationForTriviallySatisfiedRule(contextAtom, printableArgs, printableTalkingPredicates,
                    printablePredicateArgs, printableBeliefValues, renderer, whyNotLower, consequent, consequentArgs,
                    consequentPredicate);
        }

        // Intro, then:
        // TODO update this to the new pattern:
        // [This antecedent] and [antecedent atom w/ value] determine a minimum value for [consequent].
        // Since [consequent] is only [score], [antecedent] cannot be higher than [max_score].

        StringBuilder sb = new StringBuilder();
        sb.append(getIntroductorySentence()).append(" ");

        int negativeArgs = printableArgs.size() - positiveArgs;

        sb.append("This is ").append(whyNotLower ? "supported" : "limited");

        if (printableArgs.size() == 0)
            return sb.append(" by the rule '").append(getVerbalization()).append("'").toString();

        if (directFormulation) sb.append(" by ");
        else sb.append(" because ");

        if (!directFormulation) {
            sb.append("Otherwise, ");
            if (positiveArgs > 1) sb.append("one of ");
        }

        if (positiveArgs > 0) {
            List<String> components = new ArrayList<String>();
            for (int i = 0; i < positiveArgs; i++) {
                StringBuilder sbComponent = new StringBuilder();
                addURL(printableArgs, printableTalkingPredicates, printablePredicateArgs, i, sbComponent, renderer);
                sbComponent.append(" reaching a ");
                boolean verbalizeAsHighLow =
                        printableTalkingPredicates != null && printableTalkingPredicates.get(i).verbalizeOnHighLowScale;
                if (verbalizeAsHighLow) {
                    sbComponent.append("similarity");
                } else {
                    sbComponent.append("confidence");
                }
                sbComponent.append(" level of \\textit{");
                if (verbalizeAsHighLow) {
                    sbComponent.append(BeliefScale.verbalizeBeliefAsAdjectiveHigh(printableBeliefValues.get(i)));
                } else if (printableBeliefValues != null) {
                    sbComponent.append(BeliefScale.verbalizeBeliefAsAdjective(printableBeliefValues.get(i)));
                } else {
                    // This shouldn't happen.
                    sbComponent.append("???");
                }
                sbComponent.append("}");
                components.add(sbComponent.toString());
            }
            appendAnd(components, sb, false);
            if (negativeArgs > 0) sb.append(", and ");
        }

        if (negativeArgs > 0) {
            List<String> components = new ArrayList<String>();
            for (int i = positiveArgs; i < printableArgs.size(); i++) {
                StringBuilder sbComponent = new StringBuilder();
                addURL(printableArgs, printableTalkingPredicates, printablePredicateArgs, i, sbComponent, renderer);
                sbComponent.append(" being only ");
                if (printableTalkingPredicates != null && printableBeliefValues != null) {
                    if (printableTalkingPredicates.get(i).verbalizeOnHighLowScale) {
                        sbComponent.append(BeliefScale.verbalizeBeliefAsAdjectiveHigh(printableBeliefValues.get(i)));
                    } else {
                        sbComponent.append(BeliefScale.verbalizeBeliefAsAdjective(printableBeliefValues.get(i)));
                    }
                } else {
                    if (printableBeliefValues != null) {
                        sbComponent.append(BeliefScale.verbalizeBeliefAsAdjective(printableBeliefValues.get(i)));
                    } else {
                        // This shouldn't happen.
                        sbComponent.append("???");
                    }
                }
                components.add(sbComponent.toString());
            }
            appendAnd(components, sb, false);
        }

        sb.append(".");
        return sb.toString();
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
        return new HashMap[]{atoms, beliefValues, arguments};
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
        return new HashMap[]{atomsLite, beliefValues, beliefValuesLite, argumentsLite};
    }

    // This should not contain '\t'!
    public abstract String getSerializedParameters();

}

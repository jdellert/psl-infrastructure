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
import de.tuebingen.sfs.psl.engine.RuleAtomGraph;
import de.tuebingen.sfs.psl.talk.BeliefScale;
import de.tuebingen.sfs.psl.talk.ConstantRenderer;
import de.tuebingen.sfs.psl.talk.PrintableAtom;
import de.tuebingen.sfs.psl.talk.pred.NotEqualPred;
import de.tuebingen.sfs.psl.talk.pred.TalkingPredicate;
import de.tuebingen.sfs.psl.util.data.StringUtils;
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

import static de.tuebingen.sfs.psl.talk.rule.SentenceHelper.*;

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
            double weight = 1.0;
            if (ruleString.contains(":")) {
                try {
                    String[] fields = StringUtils.split(ruleString, ':');
                    weight = Double.parseDouble(fields[0].strip());
                    ruleString = fields[1].strip();
                } catch (Exception e) {
                    weight = 1.0;
                }
            }
            if (rule instanceof AbstractLogicalRule)
                return new TalkingLogicalRule(name, weight, ruleString, rule, pslProblem, verbalization);
            if (rule instanceof AbstractArithmeticRule)
                return new TalkingArithmeticRule(name, weight, ruleString, rule, pslProblem, verbalization);
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
        return verbalization.endsWith(".") ? verbalization : verbalization + ".";
    }

    /**
     * Returns the explanation for a rule with a context atom that's on the PSL problem's ignore list.
     *
     * @param contextAtom    Atom displayed in the fact window
     * @param printableAtoms All arguments of the ground rule that are eligible for
     *                       printing (i.e. unequal to the context atom and not on the ignore list)
     * @param renderer       Renderer for the atom arguments. Can be null.
     * @return the explanation
     */
    public String getContextlessExplanation(PrintableAtom contextAtom, List<PrintableAtom> printableAtoms,
                                            ConstantRenderer renderer) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIntroductorySentence()).append(" This rule ");
        if (printableAtoms.size() > 0) {
            sb.append("links ");
            addNpWithoutUrl(contextAtom, sb, renderer);
            sb.append(" to ");
            appendAnd(printableAtoms, sb, renderer); // method adds atom URLs
        } else {
            sb.append("includes ");
            addNpWithoutUrl(contextAtom, sb, renderer);
        }
        sb.append(" with unknown effects.");
        return sb.toString();
    }

    /**
     * Returns the explanation for a rule with a context atom that cannot be pushed further in the direction the rule
     * exerts pressure.
     *
     * @param printableAtoms All arguments of the ground rule that are eligible for
     *                       printing (i.e. unequal to the context atom and not on the ignore list)
     * @param renderer       Renderer for the atom arguments. Can be null.
     * @return the explanation
     */
    public String getExplanationForPolarAtom(List<PrintableAtom> printableAtoms, ConstantRenderer renderer) {
        // If the context atom is 1.0/0.0 and can't be higher/lower:
        // Intro, then, in parentheses, list the atom links/values.
        return getMinimalExplanation(printableAtoms, renderer);
    }

    /**
     * Returns a very minimal explanation consisting of an introductory sentence followed by a list of links to
     * connected atoms (when possible, verbalized) and their belief values.
     *
     * @param printableAtoms All arguments of the ground rule that are eligible for
     *                       printing (i.e. unequal to the context atom and not on the ignore list)
     * @param renderer       Renderer for the atom arguments. Can be null.
     * @return explanation
     */
    public String getMinimalExplanation(List<PrintableAtom> printableAtoms, ConstantRenderer renderer) {
        // Intro, then, in parentheses, list the atom links/values.
        StringBuilder sb = new StringBuilder();
        sb.append(getIntroductorySentence()).append(" (");

        int numArgs = printableAtoms.size();
        for (int i = 0; i < numArgs; i++) {
            addSentenceWithUrl(printableAtoms, i, sb, renderer);
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
     * @param printableAtoms    All arguments of the ground rule that are eligible for
     *                          printing (i.e. unequal to the context atom and not on the ignore list)
     * @param renderer          Renderer for the atom arguments. Can be null.
     * @param negatedConsequent Whether the consequent of the rule is negated in the ruleString
     * @param consequent        The atom in the consequent of the rule.
     * @return the verbalization
     */
    public String getExplanationForConsequent(List<PrintableAtom> printableAtoms, ConstantRenderer renderer,
                                              boolean negatedConsequent, PrintableAtom consequent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIntroductorySentence()).append(" ");
        sb.append("Since ");
        int numArgs = printableAtoms.size();
        for (int i = 0; i < numArgs; i++) {
            addSentenceWithUrl(printableAtoms, i, sb, renderer);
            if (i == numArgs - 1) {
                sb.append(" and ");
            } else {
                sb.append(", ");
            }
        }
        sb.append("the value of ");
        addNpWithoutUrl(consequent, sb, renderer);
        sb.append(" has a");
        if (negatedConsequent) {
            sb.append("n upper");
        } else {
            sb.append(" lower");
        }
        sb.append(" limit.");
        // TODO Tie this to the RuleAtomGraph's computeBodyScore method
        // and the minimum/maximum methods in the BeliefScale class instead.
        // -> "The atom needs to be at least X."

        return sb.toString();
    }

    /**
     * Returns the explanation for a context atom in a rule that is trivially satisfied by the consequent's value.
     * If the context atom is in the consequent of the rule, use the method {@link #getExplanationForConsequent(
     *List, ConstantRenderer, boolean, PrintableAtom)} instead.
     *
     * @param contextAtom       Atom displayed in the fact window.
     * @param printableAtoms    All arguments of the ground rule that are eligible for
     *                          printing (i.e. unequal to the context atom and the consequent, and not on the ignore list)
     * @param renderer          Renderer for the atom arguments. Can be null.
     * @param negatedConsequent Whether the consequent of the rule is negated in the ruleString
     * @param consequent        The atom in the consequent of the rule. NOT identical to the context atom!
     * @return the verbalization
     */
    public String getExplanationForTriviallySatisfiedRule(PrintableAtom contextAtom, List<PrintableAtom> printableAtoms,
                                                          ConstantRenderer renderer, boolean negatedConsequent,
                                                          PrintableAtom consequent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIntroductorySentence()).append(" ");
        sb.append("In this case, ");
        addNpWithoutUrl(contextAtom, sb, renderer);
        int numArgs = printableAtoms.size();
        if (numArgs == 1) {
            sb.append(" and ");
        } else if (numArgs > 1) {
            sb.append(", ");
        }
        for (int i = 0; i < numArgs; i++) {
            addNpWithUrl(printableAtoms, i, sb, renderer);
            if (i == numArgs - 2) {
                sb.append(" and");
            } else if (i < numArgs - 1) {
                sb.append(",");
            }
            sb.append(" ");
        }

        sb.append("determine");
        if (numArgs == 0) {
            sb.append("s");
        }
        sb.append(" a ");
        if (negatedConsequent) {
            sb.append("maximum");
        } else {
            sb.append("minimum");
        }
        sb.append(" value for ");
        addNpWithUrl(consequent, sb, renderer);
        sb.append(". However, since it already has a value of ");
        if (negatedConsequent) {
            sb.append("0 %");
        } else {
            sb.append("100 %");
        }
        sb.append(", changing the value of any of the other atoms would not violate this rule.");
        return sb.toString();
    }

    /**
     * Only lists the atoms and their values.
     * Used when we don't have enough information to generate a more useful explanation (i.e. information on the consequent).
     *
     * @param printableAtoms All arguments of the ground rule that are eligible for
     *                       printing (i.e. unequal to the context atom and not on the ignore list)
     * @param whyNotLower    If true: explanation appears in the WHY block, otherwise: in
     *                       the WHY NOT block.
     * @param renderer       Renderer for the atom arguments. Can be null.
     * @return the explanation
     */
    public String getBarebonesExplanation(List<PrintableAtom> printableAtoms, boolean whyNotLower,
                                          ConstantRenderer renderer) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIntroductorySentence()).append(" ");

        sb.append("This is ").append(whyNotLower ? "supported" : "limited");

        if (printableAtoms.size() == 0)
            return sb.append(" by the rule '").append(getVerbalization()).append("'").toString();

        sb.append(" by ");
        List<String> components = new ArrayList<>();
        for (int i = 0; i < printableAtoms.size(); i++) {
            StringBuilder sbComponent = new StringBuilder();
            addNpWithUrl(printableAtoms, i, sbComponent, renderer);
            sbComponent.append(" being ");
            if (printableAtoms.get(i).canTalk()) {
                if (printableAtoms.get(i).getPred().verbalizeOnHighLowScale) {
                    sbComponent.append(BeliefScale.verbalizeBeliefAsAdjectiveHigh(printableAtoms.get(i).getBelief()));
                } else {
                    sbComponent.append(BeliefScale.verbalizeBeliefAsAdjective(printableAtoms.get(i).getBelief()));
                }
            } else {
                sbComponent.append(String.format("%.2f", printableAtoms.get(i).getBelief()));
            }
            components.add(sbComponent.toString());
        }
        appendAnd(components, sb, false);

        sb.append(".");
        return sb.toString();
    }

    /**
     * Generates the explanation for an antecedent (body argument) in a logical rule.
     *
     * @param contextAtom       Atom displayed in the fact window
     * @param printableAtoms    All arguments of the ground rule that are eligible for
     *                          printing (i.e. unequal to the context atom and the consequent, and not on the ignore list)
     * @param renderer          Renderer for the atom arguments. Can be null.
     * @param negatedConsequent Whether the consequent of the rule is negated in the ruleString
     * @param consequent        The consequent of the rule
     * @return the explanation
     */
    public String getExplanationForAntecedent(PrintableAtom contextAtom, List<PrintableAtom> printableAtoms,
                                              ConstantRenderer renderer, boolean negatedConsequent,
                                              PrintableAtom consequent) {
        // Intro, then:
        // [This antecedent] and [antecedent atom w/ value] determine a minimum/maximum value for [consequent].
        // Since [consequent] is only/already [score], [antecedent] cannot be higher than [max_score].

        StringBuilder sb = new StringBuilder();
        sb.append(getIntroductorySentence()).append(" ");
        addNpWithoutUrl(contextAtom, sb, renderer);
        int numArgs = printableAtoms.size();
        if (numArgs > 0) {
            sb.append(" and ");
        }
        for (int i = 0; i < numArgs; i++) {
            addNpWithUrl(printableAtoms, i, sb, renderer);
            if (i == numArgs - 2) {
                sb.append(" and");
            } else if (i < numArgs - 1) {
                sb.append(",");
            }
            sb.append(" ");
        }
        sb.append("determine");
        if (numArgs == 0) {
            sb.append("s");
        }
        sb.append(" a ");
        if (negatedConsequent) {
            sb.append("maximum");
        } else {
            sb.append("minimum");
        }
        sb.append(" value for ");
        addNpWithUrl(consequent, sb, renderer);
        sb.append(". Since ");
        addNpWithoutUrl(consequent, sb, renderer);
        if (negatedConsequent) {
            sb.append(" already reaches a ");
            if (consequent.getPred().verbalizeOnHighLowScale) {
                sb.append("similarity");
            } else {
                sb.append("confidence");
            }
            sb.append(" level of \\textit{");
            if (consequent.canTalk() && consequent.getPred().verbalizeOnHighLowScale) {
                sb.append(BeliefScale.verbalizeBeliefAsAdjectiveHigh(consequent.getBelief()));
            } else {
                sb.append(BeliefScale.verbalizeBeliefAsAdjective(consequent.getBelief()));
            }
            sb.append("}");
        } else {
            if (consequent.canTalk() && consequent.getPred().verbalizeOnHighLowScale) {
                sb.append("is ");
                sb.append(BeliefScale.verbalizeBeliefAsSimilarityWithOnly(consequent.getBelief()));
            } else {
                BeliefScale.verbalizeBeliefAsPredicateWithOnly(consequent.getBelief());
            }
        }
        sb.append(", the possible value for ");
        addNpWithoutUrl(contextAtom, sb, renderer);
        sb.append(" is limited.");
        return sb.toString();
    }

    /**
     * Generates an explanation for logical rules and for non-equative arithmetic rules.
     *
     * @param contextAtom       Atom displayed in the fact window
     * @param contextFound      Was the context found amongst the arguments of the ground
     *                          rule? If not, its predicate is closed or it is on the RAG's
     *                          renderer's ignore list.
     * @param printableAtoms    Arguments of the ground rule, except for the context atom and the consequent
     * @param whyNotLower       If true: explanation appears in the WHY block, otherwise: in
     *                          the WHY NOT block.
     * @param renderer          Renderer for the atom arguments. Can be null.
     * @param negatedConsequent True if consequent is negated. Only matters if consequent != null.
     * @param consequent        The (singular) consequent/head of a logical rule. Can be null. Can be identical to the contextAtom.
     * @return Unequative explanation
     */
    String getUnequativeExplanation(PrintableAtom contextAtom, boolean contextFound, List<PrintableAtom> printableAtoms,
                                    boolean whyNotLower, ConstantRenderer renderer, boolean negatedConsequent,
                                    PrintableAtom consequent) {
        if (!contextFound) {
            // Context atom is not among given ground atoms (e.g. because it's on the problem's ignore list)
            if (consequent != null) {
                printableAtoms.add(consequent);
            }
            return getContextlessExplanation(contextAtom, printableAtoms, renderer);
        }

        if ((contextAtom.getBelief() > 1 - RuleAtomGraph.DISSATISFACTION_PRECISION && !whyNotLower) ||
                (contextAtom.getBelief() < RuleAtomGraph.DISSATISFACTION_PRECISION && whyNotLower)) {
            // "Why is this atom with value 1.0 not higher?" or
            // "Why is this atom with value 0.0 not lower?"
            if (consequent != null) {
                printableAtoms.add(consequent);
            }
            return getExplanationForPolarAtom(printableAtoms, renderer);
        }

        if (contextAtom.equals(consequent)) {
            return getExplanationForConsequent(printableAtoms, renderer, negatedConsequent, consequent);
        }

        if (consequent == null) {
            return getBarebonesExplanation(printableAtoms, whyNotLower, renderer);
        }

        if ((negatedConsequent && consequent.getBelief() < RuleAtomGraph.DISSATISFACTION_PRECISION) ||
                (!negatedConsequent && consequent.getBelief() > 1 - RuleAtomGraph.DISSATISFACTION_PRECISION)) {
            // The rule is trivially satisfied.
            return getExplanationForTriviallySatisfiedRule(contextAtom, printableAtoms, renderer, negatedConsequent,
                    consequent);
        }

        return getExplanationForAntecedent(contextAtom, printableAtoms, renderer, negatedConsequent, consequent);
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

    /**
     * This should not contain '\t'!
     */
    public abstract String getSerializedParameters();

    public static String escapeForURL(String s) {
        s = s.replaceAll("(?!\\\\)\\[", "\\\\[");
        s = s.replaceAll("(?!\\\\)\\]", "\\\\]");
        s = s.replaceAll("(?!\\\\)\\{", "\\\\{");
        s = s.replaceAll("(?!\\\\)\\}", "\\\\}");
        return s;
    }

    /**
     * Fills the provided list of printable atoms and updates the context atom.
     *
     * @param atomToStatus           The ground atoms and their statuses in the RAG
     * @param rag                    The rule-atom graph
     * @param nameToTalkingPredicate The map of predicates to their TalkingPredicate classes
     * @param contextAtom            The ground atom in the focus of the FactWindow
     * @param printableAtoms         The to-be-filled list of ground atoms.
     * @param statuses               The to-be-filled list of atom statuses.
     * @return the PrintableAtom instance of the contextAtom
     */
    PrintableAtom extractAtoms(List<Tuple> atomToStatus, RuleAtomGraph rag,
                               Map<String, TalkingPredicate> nameToTalkingPredicate, String contextAtom,
                               List<PrintableAtom> printableAtoms, List<String> statuses) {
        PrintableAtom printableContextAtom = null;
        for (Tuple tuple : atomToStatus) {
            String atom = tuple.get(0);
            if (atom.startsWith(NotEqualPred.SYMBOL)) {
                continue;
            }
            if (atom.contains("=")) {
                // Skip (non-)identity checks like (X != Y)
                continue;
            }

            String[] predDetails = StringUtils.split(atom, '(');
            if (rag.getIgnoredPredicates().contains(predDetails[0])) {
                continue;
            }
            PrintableAtom talkingAtom = new PrintableAtom(atom, nameToTalkingPredicate.get(predDetails[0]),
                    StringUtils.split(predDetails[1].substring(0, predDetails[1].length() - 1), ","),
                    rag.getValue(atom));

            if (atom.equals(contextAtom)) {
                printableContextAtom = talkingAtom;
                continue;
            }
            printableAtoms.add(talkingAtom);
            statuses.add(tuple.get(1));
        }
        return printableContextAtom;
    }
}

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
package de.tuebingen.sfs.psl.talk;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.engine.RuleAtomGraph;
import de.tuebingen.sfs.psl.util.data.StringUtils;
import de.tuebingen.sfs.psl.util.data.Tuple;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.formula.FormulaAnalysis;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.logical.AbstractLogicalRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class TalkingLogicalRuleOrConstraint extends TalkingRuleOrConstraint {

    // The first [positiveArgs] entries of super.args are positive literals, the
    // rest is negative
    private int positiveArgs = -1;

    public TalkingLogicalRuleOrConstraint(String name, String ruleString, PslProblem pslProblem) {
        this(name, ruleString, createRule(pslProblem.getDataStore(), ruleString), pslProblem, null);
    }

    public TalkingLogicalRuleOrConstraint(String name, String ruleString, PslProblem pslProblem, String verbalization) {
        this(name, ruleString, createRule(pslProblem.getDataStore(), ruleString), pslProblem, verbalization);
    }

    public TalkingLogicalRuleOrConstraint(String name, String ruleString, Rule rule, PslProblem pslProblem) {
        this(name, ruleString, rule, pslProblem, null);
    }

    public TalkingLogicalRuleOrConstraint(String name, String ruleString, Rule rule, PslProblem pslProblem, String verbalization) {
        super(name, ruleString, rule, pslProblem, verbalization);

        if (rule instanceof AbstractLogicalRule) {
            AbstractLogicalRule logRule = (AbstractLogicalRule) rule;
            FormulaAnalysis.DNFClause dnf = logRule.getNegatedDNF();
            List<Atom> posLit = dnf.getPosLiterals();
            positiveArgs = posLit.size();
        }
    }

    // For serialization.
    public TalkingLogicalRuleOrConstraint(String name, String ruleString) {
        super(name, ruleString);
    }

    // For serialization.
    public TalkingLogicalRuleOrConstraint(String name, String ruleString, String verbalization) {
        super(name, ruleString, verbalization);
    }

    // For serialization.
    // Override me!
    public TalkingLogicalRuleOrConstraint(String serializedParameters) {
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

    @Override
    public String getDefaultExplanation(ConstantRenderer renderer, String groundingName, String contextAtom,
                                        RuleAtomGraph rag, boolean whyExplanation) {
        return getDefaultExplanation(renderer, groundingName, contextAtom, rag, true, whyExplanation);
    }

    public String getDefaultExplanation(String groundingName, String contextAtom,
                                        RuleAtomGraph rag, boolean whyExplanation) {
        return getDefaultExplanation(null, groundingName, contextAtom, rag, true, whyExplanation);
    }

    public String generateExplanation(String groundingName, String contextAtom,
                                        RuleAtomGraph rag, boolean whyExplanation) {
        return generateExplanation(null, groundingName, contextAtom, rag, whyExplanation);
    }

    public String generateExplanation(ConstantRenderer renderer, String groundingName, String contextAtom,
                                      RuleAtomGraph rag, boolean whyExplanation) {
        return getDefaultExplanation(renderer, groundingName, contextAtom, rag, true, whyExplanation);
    }

    public String getDefaultExplanation(ConstantRenderer renderer, String groundingName, String contextAtom,
                                        RuleAtomGraph rag, boolean directFormulation, boolean whyExplanation) {
        String[] args = getArgs();
        // Get arguments of ground rule
        List<Tuple> atomToStatus = rag.getLinkedAtomsForGroundingWithLinkStatusAsList(groundingName);

        // All arguments of the ground rule that are eligible for printing (i.e.
        // open and unequal to the context atom)
        List<String> printableArgs = new ArrayList<>();
        List<TalkingPredicate> printableTalkingPredicates = new ArrayList<>();
        List<String[]> printablePredicateArgs = new ArrayList<>();
        List<Double> printableBeliefValues = new ArrayList<>();
        // Index of context atom in super.args
        int contextIndex = -1;
        // Amount of atoms in super.args that do not occur in atomToStatus
        // because they are on the renderer's ignore list
        int skip = 0;
        // Number of positive literals in printableArgs
        int positiveGroundArgs = -1;

        Map<String, TalkingPredicate> nameToTalkingPredicate = getTalkingPredicates();

        for (int i = 0; i < args.length; i++) {
            if (i == positiveArgs)
                positiveGroundArgs = printableArgs.size();

            if (rag.getIgnoredPredicates().contains(args[i].substring(0, args[i].indexOf('('))) || args[i].charAt(0) == '(')
                // Skip (X == 'x')
                // TODO: Improve
                skip++;
            else {
                String groundAtom = atomToStatus.get(i - skip).get(0);
                if (groundAtom.equals(contextAtom)) {
                    contextIndex = i;
                } else {
                    printableArgs.add(groundAtom);
                    String[] predDetails = StringUtils.split(groundAtom, '(');
                    String predName = predDetails[0];
                    String[] predArgs = StringUtils.split(predDetails[1].substring(0, predDetails[1].length() - 1), ", ");
                    if (predName.equals("#notequal")) {
                        continue;
//                        printableTalkingPredicates.add(new NotEqualPred());
                    } else {
                        printableTalkingPredicates.add(nameToTalkingPredicate.get(predName));
                    }
                    printablePredicateArgs.add(predArgs);
                    printableBeliefValues.add(rag.getValue(groundAtom));
                }
            }
        }
        if (positiveGroundArgs < 0)
            positiveGroundArgs = printableArgs.size();

        boolean contextFound = contextIndex >= 0;
        boolean contextPositive = contextIndex < positiveArgs;
        return getUnequativeExplanation(contextAtom, rag.getValue(contextAtom), contextFound, contextPositive,
                printableArgs, printableTalkingPredicates, printablePredicateArgs, printableBeliefValues,
                positiveGroundArgs, directFormulation, whyExplanation, renderer);
    }

}
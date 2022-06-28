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
import de.tuebingen.sfs.psl.talk.ConstantRenderer;
import de.tuebingen.sfs.psl.talk.PrintableAtom;
import de.tuebingen.sfs.psl.talk.pred.TalkingPredicate;
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

    public TalkingLogicalRuleOrConstraint(String name, String ruleString, Rule rule, PslProblem pslProblem,
                                          String verbalization) {
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
        if (parameters.length > 2) setVerbalization(parameters[2]);
    }

    // Override me! Can just return "" if your talking rule doesn't need serialized parameters.
    @Override
    public String getSerializedParameters() {
        return getName() + "-" + getRuleString() + (getVerbalization() != null ? "-" + getVerbalization() : "");
    }

    public String generateExplanation(String groundingName, String contextAtom, RuleAtomGraph rag,
                                      boolean whyExplanation) {
        return generateExplanation(null, groundingName, contextAtom, rag, whyExplanation);
    }

    @Override
    public String generateExplanation(ConstantRenderer renderer, String groundingName, String contextAtom,
                                      RuleAtomGraph rag, boolean whyExplanation) {
        return getDefaultExplanation(renderer, groundingName, contextAtom, rag, whyExplanation);
    }

    @Override
    public String getDefaultExplanation(ConstantRenderer renderer, String groundingName, String contextAtom,
                                        RuleAtomGraph rag, boolean whyExplanation) {
        String[] args = getArgs();
        // Get arguments of ground rule
        List<Tuple> atomToStatus = rag.getLinkedAtomsForGroundingWithLinkStatusAsList(groundingName);
        Map<String, TalkingPredicate> nameToTalkingPredicate = getTalkingPredicates();

        // Get the head of the rule, if it is *not* a disjunction but just a singular atom.
        String consequent = null;
        boolean negatedConsequent = false;
        PrintableAtom printableConsequent = null;
        String rule = getRuleString();
        if (rule.contains("->")) {
            consequent = rule.split("->")[1];
        } else if (rule.contains(">>")) {
            consequent = rule.split(">>")[1];
        } else if (rule.contains("<-")) {
            consequent = rule.split("<-")[0];
        } else if (rule.contains("<<")) {
            consequent = rule.split("<<")[0];
        }
        if (consequent == null || consequent.contains("|")) {
            // Default explanations for consequents with disjunctions
            consequent = null;
        } else {
            consequent = consequent.strip();
            negatedConsequent = consequent.startsWith("~") || consequent.startsWith("!");
            if (negatedConsequent) {
                consequent = consequent.substring(1).strip();
            }
            printableConsequent = new PrintableAtom(consequent);
            String[] predDetails = StringUtils.split(consequent, '(');
            printableConsequent.setPred(nameToTalkingPredicate.get(predDetails[0]));
            // Placeholder args:
            printableConsequent.setArgs(predDetails[1].substring(0, predDetails[1].length() - 1).split(",\\s?"));
        }

        // All arguments of the ground rule that are eligible for printing (unequal to the context atom and unequal to the consequent atom)
        List<PrintableAtom> printableAtoms = new ArrayList<>();
        List<String> statuses = new ArrayList<>();
        // Extract the printableAtoms and update the context atom.
        PrintableAtom printableContextAtom = extractAtoms(atomToStatus, rag, nameToTalkingPredicate, contextAtom,
                printableAtoms, statuses);
        boolean contextFound = printableContextAtom != null;
        if (!contextFound) {
            printableContextAtom = new PrintableAtom(contextAtom);
            printableContextAtom.setBelief(rag.getValue(contextAtom));
        }

        // Find the consequent (the version taken from the rule at the beginning of the method doesn't have the actual arguments yet):
        List<PrintableAtom> possibleMatches = new ArrayList<>();
        for (int i = 0; i < printableAtoms.size(); i++) {
            if (samePredicateArityPolarity(printableConsequent, printableAtoms.get(i), negatedConsequent,
                    statuses.get(i))) {
                possibleMatches.add(printableAtoms.get(i));
            }
        }
        if (possibleMatches.isEmpty()) {
            // Something went wrong!
            System.err.println("Couldn't find any ground instance of the consequent " + printableConsequent.getAtom() +
                    " in the rule grounding for " + getName() + ".");
            printableConsequent = null;
        } else if (possibleMatches.size() == 1) {
            printableConsequent.setAtom(possibleMatches.get(0).getAtom());
            printableConsequent.setArgs(possibleMatches.get(0).getArgs());
            printableConsequent.setBelief(possibleMatches.get(0).getBelief());
        } else {
            // TODO more elaborate parsing necessary
            printableConsequent = null;
        }

        return getUnequativeExplanation(printableContextAtom, contextFound, printableAtoms, whyExplanation, renderer,
                negatedConsequent, printableConsequent);
    }

    private boolean samePredicateArityPolarity(PrintableAtom consequent, PrintableAtom atom, boolean negatedConsequent,
                                               String atomStatus) {
        if (consequent == null) {
            return false;
        }
        if (negatedConsequent ? "+".equals(atomStatus) : "-".equals(atomStatus)) {
            return false;
        }
        if (consequent.getArgs().length != atom.getArgs().length) {
            return false;
        }
        if (consequent.getPred() == null) {
            if (atom.getPred() != null) {
                return false;
            }
            return StringUtils.split(consequent.getAtom(), '(')[0].equals(StringUtils.split(atom.getAtom(), '(')[0]);
        }
        return consequent.getPred().equals(atom.getPred());
    }

}

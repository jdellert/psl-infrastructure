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
package de.tuebingen.sfs.psl.eval;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.util.data.Tuple;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class DisjunctiveGoldStandard implements GoldStandard {

    private DisjunctiveGoldStandardIterator iter;
    private PredicateEvaluationTemplate[] predicates;
    private Map<PredicateEvaluationTemplate, TreeSet<Arguments>> missingAtoms;
    private Map<PredicateEvaluationTemplate, TreeSet<Arguments>> matchedAtoms;

    public DisjunctiveGoldStandard(DisjunctiveGoldStandardIterator iter) {
        this.iter = iter;
        this.predicates = iter.getPredicates();
        this.missingAtoms = new TreeMap<>();
        this.matchedAtoms = new TreeMap<>();
        for (PredicateEvaluationTemplate pred : predicates) {
            missingAtoms.put(pred, new TreeSet<>());
            matchedAtoms.put(pred, new TreeSet<>());
        }
        Map<Tuple, ArgumentSet> argsSets = new TreeMap<>();

        while (iter.advance()) {
            TreeSet<Arguments> args = missingAtoms.get(iter.getPredicate());
            String setName = iter.getArgSet();
            System.err.println("LUFI " + iter.getPredicate().getTemplateLabel() + " [" + setName + "]");
            if (setName.isEmpty())
                args.add(new SingleArguments(iter.getArgs()));
            else {
                Tuple setKey = new Tuple(iter.getPredicate().getTemplateLabel(), setName);
                ArgumentSet argSet = argsSets.get(setKey);
                if (argSet == null) {
                    argSet = new ArgumentSet(setName);
                    argsSets.put(setKey, argSet);
                    args.add(argSet);
                }
                argSet.addArgs(iter.getArgs());
            }
        }
    }

    @Override
    public int nOfPredicates() {
        return predicates.length;
    }

    @Override
    public PredicateEvaluationTemplate[] getPredicates() {
        return predicates;
    }

    public List<PredicateEvaluationTemplate> getTemplatesFor(String predicate) {
        return Arrays.stream(predicates).filter(t -> t.getName().equals(predicate)).collect(Collectors.toList());
    }

    private Arguments getMatch(PredicateEvaluationTemplate predicate, Tuple atom, boolean missing) {
        Map<PredicateEvaluationTemplate, TreeSet<Arguments>> atoms = (missing) ? missingAtoms : matchedAtoms;
        for (Arguments gsAtom : atoms.get(predicate)) {
            if (gsAtom.matches(predicate, atom))
                return gsAtom;
        }
        return null;
    }

    @Override
    public boolean check(PredicateEvaluationTemplate predicate, Tuple atom) {
        Arguments match = getMatch(predicate, atom, true);

        if (match == null)
            return false;

        missingAtoms.get(predicate).remove(match);
        matchedAtoms.get(predicate).add(match);
        return true;
    }

    @Override
    public boolean alreadyFound(PredicateEvaluationTemplate predicate, Tuple atom) {
        return getMatch(predicate, atom, false) != null;
    }

    @Override
    public Set<Arguments> missingAtoms(PredicateEvaluationTemplate predicate) {
        return new TreeSet<>(missingAtoms.get(predicate));
    }

    @Override
    public void additionalEvaluation(PredicateEvaluationTemplate predicate, Map<Tuple, Double> foundAtoms,
                                     Map<Tuple, Double> foundNotInGSAtoms, Set<Arguments> missingAtoms,
                                     PslProblem problem, PrintStream pStream) {
        Set<Arguments> gs = new TreeSet<>(this.missingAtoms.get(predicate));
        gs.addAll(matchedAtoms.get(predicate));
        iter.additionalEvaluation(predicate, gs, foundAtoms, foundNotInGSAtoms, missingAtoms, problem, pStream);
    }

    @Override
    public List<ModelEvaluator.TabularEvaluationEntry> additionalTabularEvaluation(
            PredicateEvaluationTemplate predicate, Map<Tuple, Double> foundAtoms,
            Map<Tuple, Double> foundNotInGSAtoms, Set<Arguments> missingAtoms,
            PslProblem problem, PrintStream pStream) {
        Set<Arguments> gs = new TreeSet<>(this.missingAtoms.get(predicate));
        gs.addAll(matchedAtoms.get(predicate));
        return iter.additionalTabularEvaluation(predicate, gs, foundAtoms, foundNotInGSAtoms, missingAtoms, problem, pStream);
    }

    @Override
    public void reset() {
        for (PredicateEvaluationTemplate pred : predicates) {
            TreeSet<Arguments> matches = matchedAtoms.get(pred);
            missingAtoms.get(pred).addAll(matches);
            matches.clear();
        }
    }

}

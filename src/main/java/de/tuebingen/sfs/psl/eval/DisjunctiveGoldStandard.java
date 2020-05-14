package de.tuebingen.sfs.psl.eval;

import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.util.data.Tuple;

public class DisjunctiveGoldStandard implements GoldStandard {

    private DisjunctiveGoldStandardIterator iter;
    private PredicateEvaluationTemplate[] predicates;
    private Map<PredicateEvaluationTemplate, TreeSet<Arguments>> atoms;

    public DisjunctiveGoldStandard(DisjunctiveGoldStandardIterator iter) {
        this.iter = iter;
        this.predicates = iter.getPredicates();
        this.atoms = new TreeMap<>();
        for (PredicateEvaluationTemplate pred : predicates)
            atoms.put(pred, new TreeSet<>());
        Map<String, ArgumentSet> argsSets = new TreeMap<>();

        while (iter.advance()) {
            TreeSet<Arguments> args = atoms.get(iter.getPredicate());
            String setName = iter.getArgSet();
            if (setName.isEmpty())
                args.add(new SingleArguments(iter.getArgs()));
            else {
                ArgumentSet argSet = argsSets.get(setName);
                if (argSet == null) {
                    argSet = new ArgumentSet(setName);
                    argsSets.put(setName, argSet);
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

    public Arguments getMatch(PredicateEvaluationTemplate predicate, Tuple atom) {
            for (Arguments gsAtom : atoms.get(predicate)) {
                if (gsAtom.matches(predicate, atom))
                    return gsAtom;
            }
        return null;
    }

    @Override
    public boolean contains(PredicateEvaluationTemplate predicate, Tuple atom) {
        return getMatch(predicate, atom) != null;
    }

    @Override
    public Set<Arguments> missingAtoms(PredicateEvaluationTemplate predicate, Set<Tuple> foundAtoms) {
        Set<Arguments> missing = new TreeSet<>(atoms.get(predicate));
        for (Tuple found : foundAtoms) {
            Arguments match = getMatch(predicate, found);
            if (match != null)
                missing.remove(match);
        }
        return missing;
    }

    @Override
    public void additionalEvaluation(PredicateEvaluationTemplate predicate, Map<Tuple, Double> foundAtoms,
                                     Map<Tuple, Double> foundNotInGSAtoms, Set<Arguments> missingAtoms,
                                     PslProblem problem, PrintStream pStream) {
        iter.additionalEvaluation(predicate, new TreeSet<>(atoms.get(predicate)),
                foundAtoms, foundNotInGSAtoms, missingAtoms,
                problem, pStream);
    }

}

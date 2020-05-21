package de.tuebingen.sfs.psl.eval;

import java.io.PrintStream;
import java.util.Map;
import java.util.Set;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.util.data.Tuple;

public interface GoldStandard {

    int nOfPredicates();

    PredicateEvaluationTemplate[] getPredicates();

    boolean check(PredicateEvaluationTemplate predicate, Tuple atom);

    boolean alreadyFound(PredicateEvaluationTemplate predicate, Tuple atom);

    Set<Arguments> missingAtoms(PredicateEvaluationTemplate predicate);

    void additionalEvaluation(PredicateEvaluationTemplate predicate, Map<Tuple, Double> foundAtoms,
                              Map<Tuple, Double> foundNotInGSAtoms, Set<Arguments> missingAtoms,
                              PslProblem problem, PrintStream pStream);

    void additionalTabularEvaluation(PredicateEvaluationTemplate predicate, Map<Tuple, Double> foundAtoms,
                              Map<Tuple, Double> foundNotInGSAtoms, Set<Arguments> missingAtoms,
                              PslProblem problem, PrintStream pStream);

    void reset();
}

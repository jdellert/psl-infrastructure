package de.tuebingen.sfs.psl.eval;

import java.io.PrintStream;
import java.util.Set;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.util.data.Tuple;

public interface GoldStandard {

    int nOfPredicates();

    PredicateEvaluationTemplate[] getPredicates();

    // TODO return double in [0, 1], rename to isMatched
    boolean contains(PredicateEvaluationTemplate predicate, Tuple atom);

    Set<Arguments> missingAtoms(PredicateEvaluationTemplate predicate, Set<Tuple> foundAtoms);

    void additionalEvaluation(PredicateEvaluationTemplate predicate, Set<Tuple> foundAtoms,
                              Set<Tuple> foundNotInGSAtoms, Set<Arguments> missingAtoms,
                              PslProblem problem, PrintStream pStream);
}

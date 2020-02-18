package de.tuebingen.sfs.psl.eval;

import java.util.Set;

import de.tuebingen.sfs.psl.util.data.Tuple;

public interface GoldStandard {

    int nOfPredicates();

    PredicateEvaluationTemplate[] getPredicates();

    // TODO return double in [0, 1], rename to isMatched
    boolean contains(PredicateEvaluationTemplate predicate, Tuple atom);

    Set<Arguments> missingAtoms(PredicateEvaluationTemplate predicate, Set<Tuple> foundAtoms);

}

package de.tuebingen.sfs.psl.eval;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.util.data.Tuple;

import java.io.PrintStream;
import java.util.Set;

public interface DisjunctiveGoldStandardIterator {

    PredicateEvaluationTemplate[] getPredicates();

    boolean advance();

    PredicateEvaluationTemplate getPredicate();

    String getArgSet();

    String[] getArgs();

    void additionalEvaluation(PredicateEvaluationTemplate predicate, Set<Tuple> foundAtoms,
                              Set<Tuple> foundNotInGSAtoms, Set<Arguments> missingAtoms,
                              PslProblem problem, PrintStream pStream);

}

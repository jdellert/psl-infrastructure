package de.tuebingen.sfs.psl.eval;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.util.data.Tuple;

import java.io.PrintStream;
import java.util.Map;
import java.util.Set;

public interface DisjunctiveGoldStandardIterator {

    PredicateEvaluationTemplate[] getPredicates();

    boolean advance();

    PredicateEvaluationTemplate getPredicate();

    String getArgSet();

    String[] getArgs();

    void additionalEvaluation(PredicateEvaluationTemplate predicate, Set<Arguments> gs,
                              Map<Tuple, Double> foundAtoms, Map<Tuple, Double> foundNotInGSAtoms,
                              Set<Arguments> missingAtoms,
                              PslProblem problem, PrintStream pStream);

    void additionalTabularEvaluation(PredicateEvaluationTemplate predicate, Set<Arguments> gs,
                              Map<Tuple, Double> foundAtoms, Map<Tuple, Double> foundNotInGSAtoms,
                              Set<Arguments> missingAtoms,
                              PslProblem problem, PrintStream pStream);

}

package de.tuebingen.sfs.psl.eval;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.util.data.Tuple;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GoldStandard {

    int nOfPredicates();

    PredicateEvaluationTemplate[] getPredicates();

    boolean check(PredicateEvaluationTemplate predicate, Tuple atom);

    boolean alreadyFound(PredicateEvaluationTemplate predicate, Tuple atom);

    Set<Arguments> missingAtoms(PredicateEvaluationTemplate predicate);

    void additionalEvaluation(PredicateEvaluationTemplate predicate, Map<Tuple, Double> foundAtoms,
                              Map<Tuple, Double> foundNotInGSAtoms, Set<Arguments> missingAtoms,
                              PslProblem problem, PrintStream pStream);

    List<ModelEvaluator.TabularEvaluationEntry> additionalTabularEvaluation(
            PredicateEvaluationTemplate predicate, Map<Tuple, Double> foundAtoms,
            Map<Tuple, Double> foundNotInGSAtoms, Set<Arguments> missingAtoms,
            PslProblem problem, PrintStream pStream);

    void reset();
}

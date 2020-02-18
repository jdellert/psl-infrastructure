package de.tuebingen.sfs.psl.eval;

public interface DisjunctiveGoldStandardIterator {

    PredicateEvaluationTemplate[] getPredicates();

    boolean advance();

    PredicateEvaluationTemplate getPredicate();

    String getArgSet();

    String[] getArgs();

}

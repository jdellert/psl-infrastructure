package de.tuebingen.sfs.psl.eval;

import de.tuebingen.sfs.psl.util.data.Tuple;

public interface Arguments extends Comparable<Arguments> {

    boolean matches(PredicateEvaluationTemplate template, Tuple args);

    static boolean matchArgs(PredicateEvaluationTemplate template, String[] args1, Tuple args2) {
        for (int i = 0; i < args1.length; i++) {
            if (!template.isIgnoredArgument(i) && !args1[i].equals(args2.get(i)))
                return false;
        }
        return true;
    }

    String toString(PredicateEvaluationTemplate template);

    static String argsToString(PredicateEvaluationTemplate template, String[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (template.isIgnoredArgument(i))
                sb.append("<*>,");
            else
                sb.append(args[i]).append(",");
        }
        return sb.deleteCharAt(sb.length()-1).toString();
    }

}

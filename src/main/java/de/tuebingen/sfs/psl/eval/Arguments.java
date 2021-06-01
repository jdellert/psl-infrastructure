package de.tuebingen.sfs.psl.eval;

import de.tuebingen.sfs.psl.util.data.Tuple;

import java.util.Set;

public interface Arguments extends Comparable<Arguments> {

    static boolean matchArgs(PredicateEvaluationTemplate template, String[] args1, Tuple args2) {
        for (int i = 0; i < args1.length; i++) {
            if (!template.isIgnoredArgument(i) && !args1[i].equals(args2.get(i)))
                return false;
        }
        return true;
    }

    static String argsToString(PredicateEvaluationTemplate template, String[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (template.isIgnoredArgument(i))
                sb.append("<*>,");
            else
                sb.append(args[i]).append(",");
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }

    boolean matches(PredicateEvaluationTemplate template, Tuple args);

    String toString(PredicateEvaluationTemplate template);

    Set<String[]> getArgs();

}

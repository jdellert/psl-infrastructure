package de.tuebingen.sfs.psl.eval;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import de.tuebingen.sfs.psl.util.data.Tuple;

public class ArgumentSet implements Arguments {

    private String name;
    private Set<String[]> argSet;

    public ArgumentSet(String name) {
        this.name = name;
        this.argSet = new TreeSet<>(new StringArrayComparator());
    }

    public ArgumentSet(String name, String[] args) {
        this(name);
        addArgs(args);
    }

    public void addArgs(String[] args) {
        argSet.add(args);
    }

    @Override
    public boolean matches(PredicateEvaluationTemplate template, Tuple args) {
        return argSet.stream().anyMatch(a -> Arguments.matchArgs(template, a, args));
    }

    @Override
    public String toString(PredicateEvaluationTemplate template) {
        StringBuilder sb = new StringBuilder();
        for (String[] args : argSet)
            sb.append(template.getName())
                    .append("(")
                    .append(Arguments.argsToString(template, args))
                    .append(")")
                    .append(" | ");
        return sb.delete(sb.length() - 3, sb.length()).toString();
    }

    @Override
    public int compareTo(Arguments o) {
        if (o instanceof ArgumentSet)
            return this.name.compareTo(((ArgumentSet) o).name);
        else if (o instanceof SingleArguments)
            return 1;
        return 1;
    }
}

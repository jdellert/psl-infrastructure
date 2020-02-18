package de.tuebingen.sfs.psl.eval;

import java.util.Arrays;

import de.tuebingen.sfs.psl.util.data.StringUtils;
import de.tuebingen.sfs.psl.util.data.Tuple;

public class SingleArguments implements Arguments {

    private String[] args;

    public SingleArguments(String[] args) {
        this.args = args;
    }

    @Override
    public boolean matches(PredicateEvaluationTemplate template, Tuple args) {
        return Arguments.matchArgs(template, this.args, args);
    }

    @Override
    public String toString(PredicateEvaluationTemplate template) {
        return template.getName() + "(" + Arguments.argsToString(template, args) + ")";
    }

    @Override
    public int compareTo(Arguments o) {
        if (o instanceof SingleArguments) {
            SingleArguments os = (SingleArguments) o;
            return StringArrayComparator.compareStatic(this.args, os.args);
        }
        else if (o instanceof ArgumentSet)
            return -1;
        return -1;
    }
}

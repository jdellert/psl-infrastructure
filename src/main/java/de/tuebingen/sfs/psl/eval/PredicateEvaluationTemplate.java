package de.tuebingen.sfs.psl.eval;

import de.tuebingen.sfs.psl.util.data.Tuple;
import org.linqs.psl.model.predicate.Predicate;

public class PredicateEvaluationTemplate implements Comparable<PredicateEvaluationTemplate> {

    private String templateLabel;
    private String predicate;
    private boolean[] ignoreArguments;

    public PredicateEvaluationTemplate() {
        templateLabel = "";
        predicate = "";
        ignoreArguments = new boolean[0];
    }

    public PredicateEvaluationTemplate(Predicate predicate) {
        this(predicate.getName(), predicate.getArity());
    }

    public PredicateEvaluationTemplate(Predicate predicate, boolean[] ignoreArguments) {
        this(predicate.getName(), ignoreArguments);
    }

    public PredicateEvaluationTemplate(String name, int arity) {
        this(name, name, arity);
    }

    protected PredicateEvaluationTemplate(String templateName, String name, int arity) {
        this(templateName, name, new boolean[arity]);
    }

    public PredicateEvaluationTemplate(String name, boolean[] ignoreArguments) {
        this(name, name, ignoreArguments);
    }

    protected PredicateEvaluationTemplate(String templateName, String predicateName, boolean[] ignoreArguments) {
        this.templateLabel = templateName;
        this.predicate = predicateName;
        this.ignoreArguments = ignoreArguments;
    }

    public String getTemplateLabel() {
        return templateLabel;
    }

    public String getName() {
        return predicate;
    }

    public void ignoreArgument(int i) {
        ignoreArgument(i, true);
    }

    public void ignoreArgument(int i, boolean ignore) {
        ignoreArguments[i] = ignore;
    }

    public boolean isIgnoredArgument(int i) {
        return ignoreArguments[i];
    }

    // Override this if you want to generally ignore certain atoms
    public boolean isIgnoredAtom(Tuple arguments) {
        return false;
    }

    @Override
    public int compareTo(PredicateEvaluationTemplate o) {
        if (this == o)
            return 0;
        if (o == null)
            return 1;

        int c = templateLabel.compareTo(o.templateLabel);
        if (c != 0)
            return c;

        c = predicate.compareTo(o.predicate);
        if (c != 0)
            return c;

        for (int i = 0; i < Math.min(ignoreArguments.length, o.ignoreArguments.length); i++) {
            c = Boolean.compare(ignoreArguments[i], o.ignoreArguments[i]);
            if (c != 0)
                return c;
        }

        return 0;
    }
}

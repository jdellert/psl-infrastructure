/*
 * Copyright 2018–2022 University of Tübingen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

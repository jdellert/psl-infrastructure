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

import de.tuebingen.sfs.psl.util.data.StringUtils;
import de.tuebingen.sfs.psl.util.data.Tuple;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String[] args : argSet)
            sb.append("PRED?(")
                    .append(StringUtils.join(args, ", "))
                    .append(")")
                    .append(" | ");
        return sb.delete(sb.length() - 3, sb.length()).toString();
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
    public Set<String[]> getArgs() {
        return argSet.stream().map(x -> x.clone()).collect(Collectors.toSet());
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

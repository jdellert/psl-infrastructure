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

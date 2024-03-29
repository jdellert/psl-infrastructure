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

package de.tuebingen.sfs.psl.engine;

import de.tuebingen.sfs.psl.util.data.Multimap;
import de.tuebingen.sfs.psl.util.data.Multimap.CollectionType;
import de.tuebingen.sfs.psl.util.data.Tuple;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AtomTemplate implements Comparable<AtomTemplate> {

    public static final String ANY_CONST = "<<?>>";

    private String predicate;
    private String[] args;

    public AtomTemplate(String predicate, String[] args) {
        this.predicate = predicate;
        this.args = args;
    }

    public AtomTemplate(String predicate, List<String> args) {
        this.predicate = predicate;
        this.args = args.toArray(new String[args.size()]);
    }

    public AtomTemplate(String... predicateAndArgs) {
        this.predicate = predicateAndArgs[0];
        this.args = new String[predicateAndArgs.length - 1];
        for (int i = 0; i < args.length; i++) {
            this.args[i] = predicateAndArgs[i + 1];
        }
    }

    public static Multimap<String, AtomTemplate> sortByPredicate(Collection<AtomTemplate> atoms) {
        Multimap<String, AtomTemplate> map = new Multimap<>(CollectionType.SET);
        for (AtomTemplate atom : atoms) {
            map.put(atom.predicate, atom);
        }
        return map;
    }

    public String getPredicateName() {
        return predicate;
    }

    public void setPredicateName(String predicate) {
        this.predicate = predicate;
    }

    public String[] getArgs() {
        return args;
    }

    public void setArgs(String[] args) {
        this.args = args;
    }

    public Tuple getArgTuple() {
        Tuple t = new Tuple();
        for (int i = 0; i < args.length; i++) {
            t.addElement(args[i]);
        }
        return t;
    }

    public boolean containsWildcards() {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(ANY_CONST)) {
                return true;
            }
        }
        return false;
    }

    public String toString() {
        String argStr = Arrays.asList(args).toString();
        return predicate + "(" + argStr.substring(1, argStr.length() - 1) + ")";
    }

    @Override
    public int compareTo(AtomTemplate o) {
        int val = predicate.compareTo(o.predicate);
        if (val == 0) {
            int len = args.length < o.args.length ? args.length : o.args.length;
            for (int i = 0; i < len; i++) {
                val = args[i].compareTo(o.args[i]);
                if (val != 0) {
                    break;
                }
            }
            if (val == 0) {
                val = args.length - o.args.length;
            }
        }
        return val;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(args);
        result = prime * result + ((predicate == null) ? 0 : predicate.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof AtomTemplate)) {
            return false;
        }
        AtomTemplate other = (AtomTemplate) obj;
        if (!Arrays.equals(args, other.args)) {
            return false;
        }
        if (predicate == null) {
            if (other.predicate != null) {
                return false;
            }
        } else if (!predicate.equals(other.predicate)) {
            return false;
        }
        return true;
    }

    public boolean argsEqualWithWildcards(List<String> other) {
        if (other == null) {
            return args == null;
        }
        return argsEqualWithWildcards(other.toArray(new String[other.size()]));
    }

    public boolean argsEqualWithWildcards(String[] other) {
        if (other == null) {
            return args == null;
        }
        if (args.length != other.length) {
            return false;
        }
        for (int i = 0; i < args.length; i++) {
            if ((!args[i].equals(other[i])) &&
                    // Wildcard argument: ANY_CONST
                    (!args[i].equals(ANY_CONST)) && (!other[i].equals(ANY_CONST))) {
                return false;
            }
        }
        return true;
    }

    public boolean equalsWithWildcards(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof AtomTemplate)) {
            return false;
        }
        AtomTemplate other = (AtomTemplate) obj;
        if (predicate == null) {
            if (other.predicate != null) {
                return false;
            }
        } else if (!predicate.equals(other.predicate)) {
            return false;
        }
        return argsEqualWithWildcards(other.args);
    }

}

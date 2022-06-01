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

import java.util.Comparator;

public class StringArrayComparator implements Comparator<String[]> {

    public static int compareStatic(String[] a, String[] b) {
        if (a == b)
            return 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int c = a[i].compareTo(b[i]);
            if (c != 0)
                return c;
        }
        return Integer.compare(a.length, b.length);
    }

    @Override
    public int compare(String[] a, String[] b) {
        return compareStatic(a, b);
    }

}

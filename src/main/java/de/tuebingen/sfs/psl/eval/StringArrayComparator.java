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

package de.tuebingen.sfs.psl.util.data;

import java.util.List;
import java.util.Map;

/**
 * Faster String split() and join() implementations.
 */
public class StringUtils {

    public static String[] split(String s, char c) {
        return split(s, c, 0);
    }

    public static String[] split(String s, char c, int minLen) {
        String[] res = new String[count(s, c, minLen) + 1];
        int i = s.indexOf(c, minLen);
        int j = -1;
        int n = 0;
        while (i >= 0) {
            res[n] = s.substring(j + 1, i);
            n++;
            j = i;
            i = s.indexOf(c, j + minLen + 1);
        }
        res[n] = s.substring(j + 1);

        return res;
    }

    public static String[] split(String s, String c) {
        String[] res = new String[count(s, c) + 1];
        int i = s.indexOf(c);
        int j = 0;
        int n = 0;
        while (i >= 0) {
            res[n] = s.substring(j, i);
            n++;
            j = i + c.length();
            i = s.indexOf(c, j);
        }
        res[n] = s.substring(j);

        return res;
    }

    public static String substringSep(String s, char c) {
        return substringSep(s, c, 0);
    }

    public static String substringSep(String s, char c, int start) {
        int i = s.indexOf(c, start);
        if (i < 0)
            return "";
        else
            return s.substring(start, i);
    }

    public static String lastSubstringSep(String s, char c) {
        return lastSubstringSep(s, c, s.length() - 1);
    }

    public static String lastSubstringSep(String s, char c, int end) {
        int i = s.lastIndexOf(c, end);
        if (i < 0)
            return "";
        else
            return s.substring(i + 1, end + 1);
    }

    public static int count(String s, char c) {
        return count(s, c, 0);
    }

    public static int count(String s, char c, int minDist) {
        int n = 0;
        int i = s.indexOf(c);
        while (i >= 0) {
            n++;
            i = s.indexOf(c, i + minDist + 1);
        }
        return n;
    }

    public static int count(String s, String c) {
        int n = 0;
        int i = s.indexOf(c);
        while (i >= 0) {
            n++;
            i = s.indexOf(c, i + 1);
        }
        return n;
    }

    public static String join(String[] a, char c) {
        return join(a, c, 0, a.length);
    }

    public static String join(String[] a, char c, int from, int to) {
        StringBuilder s = new StringBuilder();
        for (int i = from; i < to; i++)
            s.append(a[i]).append(c);
        if (s.length() > 0) s.deleteCharAt(s.length() - 1);
        return s.toString();
    }

    public static String join(Iterable<?> a, char c) {
        return join(a, c, "null");
    }

    public static String join(Iterable<?> a, char c, String nullReplacement) {
        StringBuilder s = new StringBuilder();
        for (Object p : a)
            s.append((p == null) ? nullReplacement : p.toString()).append(c);
        if (s.length() > 0) s.deleteCharAt(s.length() - 1);
        return s.toString();
    }

    public static String join(Iterable<?> a, String c) {
        return join(a, c, "null");
    }

    public static String join(Iterable<?> a, String c, String nullReplacement) {
        StringBuilder s = new StringBuilder();
        for (Object p : a)
            s.append((p == null) ? nullReplacement : p.toString()).append(c);
        if (s.length() > 0) s.delete(s.length() - c.length(), s.length());
        return s.toString();
    }

    public static String join(String[] a, String c) {
        return join(a, c, 0, a.length);
    }

    public static String join(String[] a, String c, int from, int to) {
        if (from >= to)
            return "";
        StringBuilder s = new StringBuilder();
        for (int i = from; i < to; i++)
            s.append(a[i]).append(c);
        return s.delete(s.length() - c.length(), s.length()).toString();
    }

    public static String join(List<String> a, char c, int from, int to) {
        if (from >= to)
            return "";
        StringBuilder s = new StringBuilder();
        for (int i = from; i < to; i++)
            s.append(a.get(i)).append(c);
        return s.delete(s.length() - 1, s.length()).toString();
    }

    public static String join(List<String> a, String c, int from, int to) {
        if (from >= to)
            return "";
        StringBuilder s = new StringBuilder();
        for (int i = from; i < to; i++)
            s.append(a.get(i)).append(c);
        return s.delete(s.length() - c.length(), s.length()).toString();
    }

    public static String join(int[] a, char c) {
        return join(a, c, 0, a.length);
    }

    public static String join(int[] a, char c, int from, int to) {
        if (from >= to)
            return "";
        StringBuilder s = new StringBuilder();
        for (int i = from; i < to; i++)
            s.append(a[i]).append(c);
        return s.deleteCharAt(s.length() - 1).toString();
    }

    public static String join(char[] a, char c) {
        return join(a, c, 0, a.length);
    }

    public static String join(char[] a, char c, int from, int to) {
        StringBuilder s = new StringBuilder();
        for (int i = from; i < to; i++)
            s.append(a[i]).append(c);
        if (s.length() > 0) s.deleteCharAt(s.length() - 1);
        return s.toString();
    }

    public static String joinMap(Map<String, String> map, char c) {
        StringBuilder s = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            s.append(entry.getKey()).append("=").append(entry.getValue()).append(c);
        }
        if (s.length() > 0) s.deleteCharAt(s.length() - 1); // remove the last slash
        // should the last slash be removed?
        // String.split("/") would not mind a dangling slash.
        return s.toString();
    }

    public static void main(String[] args) {
        for (String s : split("aaaabbaaabbaabba", "bb"))
            System.out.println(s);
        System.out.println(join(split("aaaabbaaabbaabba", "bb"), "bb"));
    }
}

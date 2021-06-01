package de.tuebingen.sfs.psl.util.data;

import java.util.ArrayList;
import java.util.List;

public class Tuple implements Comparable<Tuple> {
    List<String> elements;

    public Tuple() {
        this.elements = new ArrayList<String>();
    }

    public Tuple(String... elements) {
        this.elements = new ArrayList<String>(elements.length);
        for (String element : elements) {
            this.elements.add(element);
        }
    }

    public String get(int index) {
        return elements.get(index);
    }

    public int length() {
        return elements.size();
    }

    public int hashCode() {
        int code = 0;
        for (int i = 0; i < length(); i++) {
            code += get(i).hashCode();
        }
        return code;
    }

    public boolean equals(Object o) {
        if (o instanceof Tuple) {
            Tuple other = (Tuple) o;
            if (other.length() != length()) return false;
            for (int i = 0; i < length(); i++) {
                if (!other.get(i).equals(get(i))) return false;
            }
            return true;
        } else {
            return false;
        }
    }

    public String toString() {
        return String.join(",", elements);
    }

    public List<String> toList() {
        return new ArrayList<>(elements);
    }

    public List<String> toList(int from, int to) {
        return new ArrayList<>(elements.subList(from, to));
    }

    public void addElement(String element) {
        elements.add(element);
    }

    @Override
    public int compareTo(Tuple o) {
        for (int i = 0; i < Math.min(length(), o.length()); i++) {
            int positionCompare = get(i).compareTo(o.get(i));
            if (positionCompare != 0) return positionCompare;
        }
        if (length() < o.length()) return -1;
        if (length() > o.length()) return 1;
        return 0;
    }
}

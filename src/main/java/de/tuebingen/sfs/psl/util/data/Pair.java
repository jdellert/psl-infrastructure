package de.tuebingen.sfs.psl.util.data;

public class Pair<T, U> {
    public T first;
    public U second;

    public Pair(T first, U second) {
        this.first = first;
        this.second = second;
    }

    public String toString() {
        return "(" + first + "," + second + ")";
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public boolean equals(Object o) {
        if (o instanceof Pair<?, ?>) {
            Pair<?, ?> otherPair = (Pair<?, ?>) o;
            return (otherPair.first.equals(first) && otherPair.second.equals(second));
        }
        return false;
    }
}

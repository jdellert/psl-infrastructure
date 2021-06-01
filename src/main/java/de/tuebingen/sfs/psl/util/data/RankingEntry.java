package de.tuebingen.sfs.psl.util.data;

public class RankingEntry<T> implements Comparable<RankingEntry<T>> {
    public T key;
    public String extraInformation = null;
    public double value;

    public RankingEntry(T key, double value) {
        this.key = key;
        this.value = value;
    }

    public RankingEntry(T key, String extraInformation, double value) {
        this.key = key;
        this.extraInformation = extraInformation;
        this.value = value;
    }

    public boolean equals(Object o) {
        if (o instanceof RankingEntry) {
            RankingEntry<T> otherEntry = (RankingEntry<T>) o;
            if (this.value != otherEntry.value) return false;
            return this.key.equals(otherEntry.key);
        }
        return false;
    }

    public int compareTo(RankingEntry<T> otherEntry) {
        //System.err.println("Comparing ranking entries: " + this + " <-> " + otherEntry);
        if (this.value < otherEntry.value) return -1;
        if (this.value > otherEntry.value) return 1;
        if (key instanceof Comparable<?>) {
            return -((Comparable) this.key).compareTo((Comparable) otherEntry.key);
        }
        return 0;
    }

    public String toString() {
        if (extraInformation != null) {
            return key + "[" + extraInformation + "](" + value + ")";
        }
        return key + "(" + value + ")";
    }

}

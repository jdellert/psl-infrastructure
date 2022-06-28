package de.tuebingen.sfs.psl.talk;

import de.tuebingen.sfs.psl.talk.pred.TalkingPredicate;

import java.util.Objects;

public class PrintableAtom {

    String atom = null;
    TalkingPredicate pred = null;
    String[] args = null;
    double belief = -1.0;

    public PrintableAtom(String atom, TalkingPredicate pred, String[] args, double belief) {
        this.atom = atom;
        this.pred = pred;
        this.args = args;
        this.belief = belief;
    }

    public PrintableAtom(String atom) {
        this.atom = atom;
    }

    public void setAtom(String atom) {
        this.atom = atom;
    }

    public void setPred(TalkingPredicate pred) {
        this.pred = pred;
    }

    public void setArgs(String[] args) {
        this.args = args;
    }

    public void setBelief(double belief) {
        this.belief = belief;
    }

    public String getAtom() {
        return atom;
    }

    public TalkingPredicate getPred() {
        return pred;
    }

    public String[] getArgs() {
        return args;
    }

    public double getBelief() {
        return belief;
    }

    public boolean canTalk() {
        return pred != null && args != null;
    }

    @Override
    public boolean equals(Object o) {
        // Only the underlying atom is relevant.
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrintableAtom that = (PrintableAtom) o;
        return Objects.equals(atom, that.atom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(atom);
    }

    @Override
    public String toString() {
        return atom + "( %.2f)".formatted(belief);
    }
}

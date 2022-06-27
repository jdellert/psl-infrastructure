package de.tuebingen.sfs.psl.talk;

import de.tuebingen.sfs.psl.talk.rule.TalkingPredicate;

public class PrintableTalkingAtom {

    TalkingPredicate pred = null;
    String[] args = null;
    double belief = -1.0;

    public PrintableTalkingAtom(TalkingPredicate pred, String[] args, double belief) {
        this.pred = pred;
        this.args = args;
        this.belief = belief;
    }

    /**
     * Only to be used if all arguments are set later using the setter methods!
     */
    public PrintableTalkingAtom() {
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

    public TalkingPredicate getPred() {
        return pred;
    }

    public String[] getArgs() {
        return args;
    }

    public double getBelief() {
        return belief;
    }

    public boolean allFieldsSet() {
        return pred != null && args != null && belief > -0.000001;
    }
}

package de.tuebingen.sfs.psl.talk;

public interface TalkingConstraint {
    // Used for combining TalkingArithmeticConstraint and TalkingLogicalConstraint

    public static boolean isConstraint(String ruleString) {
        return ruleString.strip().endsWith(".");
    }
}

package de.tuebingen.sfs.psl.talk;

public interface TalkingRule {
    // Used for combining TalkingArithmeticRule and TalkingLogicalRule

    public static boolean isWeightedRule(String ruleString) {
        return !ruleString.strip().endsWith(".");
    }
}
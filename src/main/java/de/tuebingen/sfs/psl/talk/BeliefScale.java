package de.tuebingen.sfs.psl.talk;

import de.tuebingen.sfs.psl.engine.RuleAtomGraph;

public class BeliefScale {

    private static final Belief[] scale = new Belief[]{
            new Belief(RuleAtomGraph.DISSATISFACTION_PRECISION, "certainly false", "is certainly false",
                    "not be (extremely) unlikely", "is (extremely) unlikely", "be extremely unlikely", "certainly not",
                    "extremely low", "extremely dissimilar", "not be extremely dissimilar", "(extremely) dissimilar",
                    "be extremely dissimilar", "almost never", "extremely rare"),
            new Belief(0.05, "very unlikely", "is very unlikely", "not be (very) unlikely", "is (very) unlikely",
                    "be very unlikely", "very probably not", "very low", "very dissimilar",
                    "not be extremely dissimilar", "(very) dissimilar", "be very dissimilar", "very rarely",
                    "very rare"),
            new Belief(0.15, "unlikely", "is unlikely", "not be unlikely", "is unlikely", "be unlikely", "probably not",
                    "low", "dissimilar", "not be very dissimilar", "dissimilar", "be dissimilar", "rarely", "rare"),
            // TODO alternative to 'doubtfully' since it cannot be used in the same constructions as e.g. 'probably not'
            // * "X is doubtfully derived from Y."
            new Belief(0.33, "doubtful", "is doubtful", "not be (even moderately) unlikely", "is doubtful",
                    "be doubtful", "doubtfully", "moderately low", "moderately dissimilar",
                    "not be more than moderately dissimilar", "(moderately) dissimilar", "be dissimilar",
                    "rather rarely", "rather rare"),
            new Belief(0.50, "slightly doubtful", "is slightly doubtful", "not be (even slightly) unlikely",
                    "is (slightly) doubtful", "not be even fairly plausible", "perhaps", "slightly low",
                    "somewhat dissimilar", "not be more than somewhat dissimilar", "(somewhat) dissimilar",
                    "not be even moderately similar", "somewhat rarely", "somewhat rare"),
            new Belief(0.66, "fairly plausible", "is fairly plausible", "be at least fairly plausible",
                    "is merely fairly plausible", "not be more than fairly plausible", "fairly plausibly",
                    "slightly high", "somewhat similar", "be at least somewhat similar", "only somewhat similar",
                    "not be more than somewhat similar", "somewhat often", "somewhat frequent"),
            new Belief(0.85, "plausible", "is plausible", "at least be plausible", "is merely plausible",
                    "be moderately plausible at most", "plausibly", "moderately high", "moderately similar",
                    "at least moderately similar", "only moderately similar", "be moderately similar at most",
                    "rather often", "rather frequent"),
            new Belief(0.95, "likely", "is likely", "at least be likely", "is merely likely",
                    "not be more than moderately plausible", "probably", "high", "similar",
                    "be more than moderately similar", "not much more than moderately similar",
                    "not be \\textit{very} similar", "often", "frequent"),
            new Belief(1 - RuleAtomGraph.DISSATISFACTION_PRECISION, "very likely", "is very likely",
                    "be at least very likely", "is merely very likely", "not be certainly true", "very probably",
                    "very high", "very similar", "be at least very similar", "not quite extremely similar",
                    "not be extremely similar", "very often", "very frequent"),
            // This last threshold value needs to be above any possible belief value.
            // The 'only' and 'at most' and 'at least' entries for this belief shouldn't be used, and these cases should be expressed differently.
            new Belief(100.00, "certainly true", "is certain", "be certain", "is likely", "not be guaranteed",
                    "certainly", "extremely high", "extremely similar", "be extremely similar", "extremely similar",
                    "not be identical", "always", "extremely frequent")};

    private static String verbalizeBelief(VerbalizationType type, double belief) {
        int i;
        for (i = 0; i < scale.length; i++) {
            if (belief <= scale[i].getThreshold()) {
                break;
            }
        }
        if (belief < 0.0 || i >= scale.length) {
            System.err.println("Encountered strange belief value during verbalization: " + belief);
            return "<strange belief value>";
        }
        switch (type) {
            case ADVERB:
                return scale[i].getAdverb();
            case PREDICATE:
                return scale[i].getPredicate();
            case PREDICATE_MIN_INF:
                return scale[i].getPredicateMinInf();
            case PREDICATE_ONLY:
                return scale[i].getPredicateOnly();
            case PREDICATE_MAX_INF:
                return scale[i].getPredicateMaxInf();
            case ADJECTIVE_HIGH:
                return scale[i].getAdjectiveHigh();
            case SIMILARITY:
                return scale[i].getSimilarity();
            case SIMILARITY_MIN_INF:
                return scale[i].getSimilarityMinInf();
            case SIMILARITY_ONLY:
                return scale[i].getSimilarityOnly();
            case SIMILARITY_MAX_INF:
                return scale[i].getSimilarityMaxInf();
            case FREQUENCY_ADV:
                return scale[i].getFrequencyAdverb();
            case FREQUENCY_ADJ:
                return scale[i].getFrequencyAdjective();
            case ADJECTIVE:
            default:
                return scale[i].getAdjective();
        }
    }

    public static boolean sameBeliefInterval(double belief1, double belief2) {
        int pos1 = -1;
        int pos2 = -1;
        for (int i = 0; i < scale.length; i++) {
            if (pos1 < 0 && belief1 <= scale[i].getThreshold()) {
                pos1 = i;
                if (pos2 >= 0) {
                    break;
                }
            }
            if (pos2 < 0 && belief2 <= scale[i].getThreshold()) {
                pos2 = i;
                if (pos1 >= 0) {
                    break;
                }
            }
        }
        return pos1 == pos2;
    }

    // TODO: default behavior for closed / fixed predicates
    // (was excluded, was fixed as, was observed as ...)

    public static String verbalizeBeliefAsAdjective(double belief) {
        return verbalizeBelief(VerbalizationType.ADJECTIVE, belief);
    }

    public static String verbalizeBeliefAsPredicate(double belief) {
        return verbalizeBelief(VerbalizationType.PREDICATE, belief);
    }

    /**
     * "[The borrowing should] be at least somewhat likely." (= likely and up)
     * "[The borrowing should] not be (even moderately) unlikely." (= moderately unlikely and up)
     * "[The borrowing should] at least be plausible." (= plausible and up)
     */
    public static String verbalizeBeliefAsInfinitiveMinimumPredicate(double belief) {
        return verbalizeBelief(VerbalizationType.PREDICATE_MIN_INF, belief);
    }

    /**
     * "[However, the borrowing] is only somewhat likely." (= likely, but not higher)
     * "[However, the borrowing] is (moderately) unlikely." (= moderately unlikely, but not higher)
     * "[However, the borrowing] is merely plausible." (= plausible, but not higher)
     */
    public static String verbalizeBeliefAsPredicateWithOnly(double belief) {
        return verbalizeBelief(VerbalizationType.PREDICATE_ONLY, belief);
    }

    public static String verbalizeBeliefAsInfinitiveMaximumPredicate(double belief) {
        return verbalizeBelief(VerbalizationType.PREDICATE_MAX_INF, belief);
    }


    public static String verbalizeBeliefAsAdverb(double belief) {
        return verbalizeBelief(VerbalizationType.ADVERB, belief);
    }

    public static String verbalizeBeliefAsAdjectiveHigh(double belief) {
        return verbalizeBelief(VerbalizationType.ADJECTIVE_HIGH, belief);
    }

    public static String verbalizeBeliefAsSimilarity(double belief) {
        return verbalizeBelief(VerbalizationType.SIMILARITY, belief);
    }

    /**
     * "[The forms should] be at least somewhat similar." (= somewhat similar and up)
     * "[The forms should] not be very dissimilar." (= moderately dissimilar and up)
     */
    public static String verbalizeBeliefAsMinimumSimilarityInfinitive(double belief) {
        return verbalizeBelief(VerbalizationType.SIMILARITY_MIN_INF, belief);
    }

    /**
     * "[However, the forms are] only somewhat similar." (= somewhat similar, and not higher)
     * "[However, the forms are] (very) dissimilar." (= very dissimilar, and not higher)
     * "[However, the forms are] not quite extremely similar." (= very similar, and not higher)
     */
    public static String verbalizeBeliefAsSimilarityWithOnly(double belief) {
        return verbalizeBelief(VerbalizationType.SIMILARITY_ONLY, belief);
    }

    /**
     * "[The forms should] not be extremely similar." (= very similar, or lower)
     * "[The forms should] not be even moderately similar." (= somewhat dissimilar, or lower)
     * "[The forms should] not be more than somewhat similar." (= somewhat similar, or lower)
     */
    public static String verbalizeBeliefAsMaximumSimilarityInfinitive(double belief) {
        return verbalizeBelief(VerbalizationType.SIMILARITY_MAX_INF, belief);
    }

    public static String verbalizeBeliefAsAdverbFrequency(double belief) {
        return verbalizeBelief(VerbalizationType.FREQUENCY_ADV, belief);
    }

    public static String verbalizeBeliefAsAdjectiveFrequency(double belief) {
        return verbalizeBelief(VerbalizationType.FREQUENCY_ADJ, belief);
    }

    private enum VerbalizationType {
        ADJECTIVE, PREDICATE, PREDICATE_MIN_INF, PREDICATE_ONLY, PREDICATE_MAX_INF, ADVERB, ADJECTIVE_HIGH, SIMILARITY,
        SIMILARITY_MIN_INF, SIMILARITY_ONLY, SIMILARITY_MAX_INF, FREQUENCY_ADV, FREQUENCY_ADJ
    }

}

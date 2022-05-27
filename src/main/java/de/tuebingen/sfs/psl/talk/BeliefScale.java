package de.tuebingen.sfs.psl.talk;

public class BeliefScale {

    private static final Belief[] scale = new Belief[]{
            new Belief(0.01, "almost certainly false", "is almost certainly false", "not be (extremely) unlikely",
                    "is (extremely) unlikely", "almost certainly not", "extremely low", "extremely dissimilar",
                    "not be extremely dissimilar", "(extremely) dissimilar", "almost never", "extremely rare"),
            new Belief(0.05, "very unlikely", "is very unlikely", "not be (very) unlikely", "is (very) unlikely",
                    "very probably not", "very low", "very dissimilar", "not be extremely dissimilar",
                    "(very) dissimilar", "very rarely", "very rare"),
            new Belief(0.15, "unlikely", "is unlikely", "not be unlikely", "is unlikely", "probably not", "low",
                    "dissimilar", "not be very dissimilar", "dissimilar", "rarely", "rare"),
            // TODO alternative to 'doubtfully' since it cannot be used in the same constructions as e.g. 'probably not'
            // * "X is doubtfully derived from Y."
            new Belief(0.33, "doubtful", "is doubtful", "not be (even moderately) unlikely", "is doubtful",
                    "doubtfully", "moderately low", "moderately dissimilar", "not be more than moderately dissimilar",
                    "(moderately) dissimilar", "rather rarely", "rather rare"),
            new Belief(0.50, "slightly doubtful", "is slightly doubtful", "not be (even slightly) unlikely",
                    "is (slightly) doubtful", "perhaps", "slightly low", "somewhat dissimilar",
                    "not be more than somewhat dissimilar", "(somewhat) dissimilar", "somewhat rarely",
                    "somewhat rare"),
            new Belief(0.66, "fairly plausible", "is fairly plausible", "be at least fairly plausible",
                    "is merely fairly plausible", "fairly plausibly", "slightly high", "somewhat similar",
                    "be at least somewhat similar", "only somewhat similar", "somewhat often", "somewhat frequent"),
            new Belief(0.85, "plausible", "is plausible", "at least be plausible", "is merely plausible", "plausibly",
                    "moderately high", "moderately similar", "at least moderately similar", "only moderately similar",
                    "rather often", "rather frequent"),
            new Belief(0.95, "likely", "is likely", "at least be likely", "is merely likely", "probably", "high",
                    "similar", "be more than moderately similar", "not quite highly similar", "often", "frequent"),
            new Belief(0.99, "very likely", "is very likely", "be at least very likely", "is merely very likely",
                    "very probably", "very high", "very similar", "be at least very similar",
                    "not quite extremely similar", "very often", "very frequent"),
            // This threshold value needs to be above any possible belief value:
            new Belief(100.00, "almost certainly true", "is almost certain", "be (almost) certain", "is likely",
                    "almost certainly", "extremely high", "extremely similar", "be extremely similar",
                    "extremely similar", "almost always", "extremely frequent")};

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
            case ADJECTIVE_HIGH:
                return scale[i].getAdjectiveHigh();
            case SIMILARITY:
                return scale[i].getSimilarity();
            case SIMILARITY_MIN_INF:
                return scale[i].getSimilarityMinInf();
            case SIMILARITY_ONLY:
                return scale[i].getSimilarityOnly();
            case FREQUENCY_ADV:
                return scale[i].getFrequencyAdverb();
            case FREQUENCY_ADJ:
                return scale[i].getFrequencyAdjective();
            case ADJECTIVE:
            default:
                return scale[i].getAdjective();
        }
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

    public static String verbalizeBeliefAsAdverbFrequency(double belief) {
        return verbalizeBelief(VerbalizationType.FREQUENCY_ADV, belief);
    }

    public static String verbalizeBeliefAsAdjectiveFrequency(double belief) {
        return verbalizeBelief(VerbalizationType.FREQUENCY_ADJ, belief);
    }

    private enum VerbalizationType {
        ADJECTIVE, PREDICATE, PREDICATE_MIN_INF, PREDICATE_ONLY, ADVERB, ADJECTIVE_HIGH, SIMILARITY, SIMILARITY_MIN_INF,
        SIMILARITY_ONLY, FREQUENCY_ADV, FREQUENCY_ADJ
    }

}

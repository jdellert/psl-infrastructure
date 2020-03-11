package de.tuebingen.sfs.psl.talk;

public class BeliefScale {

	private enum VerbalizationType {
		ADJECTIVE, PREDICATE, ADVERB, ADJECTIVE_HIGH, SIMILARITY, FREQUENCY_ADV, FREQUENCY_ADJ
	}

	private static Belief[] scale = new Belief[] {
			new Belief(0.01, "almost certainly false", "is almost certainly false",
					"almost certainly not", "extremely low", "extremely dissimilar",
					"almost never", "extremely rare"),
			new Belief(0.05, "very unlikely", "is very unlikely", "very probably not",
					"very low", "very dissimilar", "very rarely", "very rare"),
			new Belief(0.15, "unlikely", "is unlikely", "probably not", "low",
					"dissimilar", "rarely", "rare"),
			new Belief(0.33, "doubtful", "is doubtful", "doubtfully",
					"moderately low", "moderately dissimilar", "rather rarely", "rather rare"),
			new Belief(0.50, "slightly doubtful", "is slightly doubtful", "perhaps",
					"slightly low", "somewhat dissimilar", "sometimes", "somewhat frequent"),
			new Belief(0.66, "fairly plausible", "is fairly plausible", "fairly plausibly",
					"slightly high", "somewhat similar", "rather often", "rather frequent"),
			new Belief(0.85, "plausible", "is plausible", "plausibly",
					"moderately high", "moderately similar", "often", "frequent"),
			new Belief(0.95, "likely", "is likely", "probably", "high",
					"similar", "very often", "very frequent"),
			new Belief(0.99, "very likely", "is very likely", "very probably",
					"very high", "very similar", "extremely often", "extremely frequent"),
			// This threshold value needs to be above any possible belief value:
			new Belief(100.00, "almost certainly true", "is almost certain",
					"almost certainly", "extremely high", "extremely similar",
					"almost always", "extremely frequent") };

	// TODO: default behavior for closed / fixed predicates
	// (was excluded, was fixed as, was observed as ...)

	private static String verbalizeBelief(VerbalizationType type, double belief) {
		int i;
		for (i = 0; i < scale.length; i++) {
			if (belief <= scale[i].getThreshold()) {
				break;
			}
		}
		if (i >= scale.length) {
			System.err.println("Encountered weird belief value during verbalization: " + belief);
			return "<weird belief value>";
		}
		switch (type) {
			case ADVERB:
				return scale[i].getAdverb();
			case PREDICATE:
				return scale[i].getPredicate();
			case ADJECTIVE_HIGH:
				return scale[i].getAdjectiveHigh();
			case SIMILARITY:
				return scale[i].getSimilarity();
			case FREQUENCY_ADV:
				return scale[i].getFrequencyAdverb();
			case FREQUENCY_ADJ:
				return scale[i].getFrequencyAdjective();
			case ADJECTIVE:
			default:
				return scale[i].getAdjective();
		}
	}

	public static String verbalizeBeliefAsAdjective(double belief) {
		return verbalizeBelief(VerbalizationType.ADJECTIVE, belief);
	}

	public static String verbalizeBeliefAsPredicate(double belief) {
		return verbalizeBelief(VerbalizationType.PREDICATE, belief);
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

	public static String verbalizeBeliefAsAdverbFrequency(double belief) {
		return verbalizeBelief(VerbalizationType.FREQUENCY_ADV, belief);
	}

	public static String verbalizeBeliefAsAdjectiveFrequency(double belief) {
		return verbalizeBelief(VerbalizationType.FREQUENCY_ADJ, belief);
	}

}

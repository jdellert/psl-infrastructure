package de.tuebingen.sfs.psl.talk;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.util.data.StringUtils;

public class TalkingPredicate {
	String predSymbol;
	int arity;
	boolean verbalizeOnHighLowScale;

	public TalkingPredicate(String predSymbol, int arity) {
		this(predSymbol, arity, false);
	}

	public TalkingPredicate(String predSymbol, int arity, boolean verbalizeOnHighLowScale) {
		this.predSymbol = predSymbol;
		this.arity = arity;
		this.verbalizeOnHighLowScale = verbalizeOnHighLowScale;
	}

	public String getSymbol() {
		return predSymbol;
	}

	public int getArity() {
		return arity;
	}

	public boolean verbalizeOnHighLowScale() {
		return verbalizeOnHighLowScale;
	}

	public String verbalizeIdea(String... args) {
		return predSymbol + "(" + StringUtils.join(args, ", ") + ")";
	}

	public String verbalizeIdeaWithBelief(double belief, String... args) {
		return verbalizeIdea(args) + " " + BeliefScale.verbalizeBeliefAsPredicate(belief);
	}

	public String verbalizeIdeaAsSentence(String... args) {
		// Override me!
		return verbalizeIdea(args);
	}
	
	public String verbalizeIdeaAsSentence(double belief, String... args) {
		// Override me!
		return verbalizeIdeaWithBelief(belief, args);
	}

	public String verbalizeIdeaAsNP(String... args) {
		// Override me!
		return verbalizeIdea(args);
	}

	@Override
	public String toString() {
		return predSymbol + "<" + arity + ">";
	}

	public static final String getPredNameFromAllCaps(String predName) {
		String prefix = "";
		if (predName.length() > 4) {
			prefix = PslProblem.predicatePrefix(predName);
		}
		return predName.substring(0, prefix.length() + 1) + predName.toLowerCase().substring(prefix.length() + 1);
	}

	public static String[] extractArgs(String atomString) {
		if (atomString == null)
			return null;
		String argString = atomString.substring(atomString.indexOf('(') + 1, atomString.length() - 1);
		return StringUtils.split(argString, ", ");
	}

}

package de.tuebingen.sfs.psl.talk;

import org.linqs.psl.util.StringUtils;

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
		return predSymbol + "(" + StringUtils.join(args, ',') + ")";
	}

	public String verbalizeIdeaWithBelief(double belief, String... args) {
		return verbalizeIdea(args) + " " + BeliefScale.verbalizeBeliefAsPredicate(belief);
	}

	public String verbalizeIdeaAsSentence(String... args) {
		// Override me!
		return verbalizeIdea(args);
	}

	public String verbalizeIdeaAsNP(String... args) {
		// Override me!
		return verbalizeIdea(args);
	}

}

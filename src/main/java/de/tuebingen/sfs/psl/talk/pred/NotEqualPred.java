package de.tuebingen.sfs.psl.talk.pred;

import de.tuebingen.sfs.psl.talk.TalkingPredicate;

public class NotEqualPred extends TalkingPredicate {
	
	// Internally used to avoid NPEs when generating default explanations for rules.
	// When writing rules use != instead: X != Y.

	public NotEqualPred() {
		super("#notequal", 2);
	}

}

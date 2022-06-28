/*
 * Copyright 2018–2022 University of Tübingen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tuebingen.sfs.psl.talk.pred;

import java.util.Arrays;
import java.util.List;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.talk.ConstantRenderer;
import de.tuebingen.sfs.psl.util.data.StringUtils;

// To use a custom class for properly rendering the PSL constants (atom arguments), create the following classes:
//- `class YourConstantRenderer`, a class that assigns human-understandable strings to PSL constants
//- `interface YourTalkingRule` 
//- `class YourTalkingLogicalRule extends TalkingLogicalRule implements YourTalkingRule`
//- `class YourTalkingArithmeticRule extends TalkingArithmeticRule implements YourTalkingRule`
//- `class YourTalkingPredicate extends TalkingPredicate`
//`YourTalkingPredicate` and `YourTalkingRule` can extend their explanation/verbalization
//methods by an additional argument: YourConstantRenderer.
//An example implementation of this will be available at
//https://github.com/jdellert/etinen-shared/tree/master/src/main/java/de/tuebingen/sfs/eie/talk
public class TalkingPredicate {
	String predSymbol;
	int arity;
	public boolean verbalizeOnHighLowScale;

	public TalkingPredicate(String predSymbol, int arity) {
		this(predSymbol, arity, false);
	}

	public TalkingPredicate(String predSymbol, int arity, boolean verbalizeOnHighLowScale) {
		this.predSymbol = predSymbol;
		this.arity = arity;
		this.verbalizeOnHighLowScale = verbalizeOnHighLowScale;
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

	public String getSymbol() {
		return predSymbol;
	}

	public int getArity() {
		return arity;
	}

	public boolean verbalizeOnHighLowScale() {
		return verbalizeOnHighLowScale;
	}

	public String verbalizeIdea(ConstantRenderer renderer, String... args) {
		return predSymbol + "(" + StringUtils.join(args, ", ") + ")";
	}

	// Override me!
	public String verbalizeIdeaWithBelief(ConstantRenderer renderer, double belief, String[] array) {
		return verbalizeIdea(renderer, array) + " %.2f".formatted(belief);
	}

	// Override me!
	public String verbalizeIdeaAsSentence(ConstantRenderer renderer, String... args) {
		return verbalizeIdea(renderer, args);
	}

	// Override me!
	public String verbalizeIdeaAsSentence(ConstantRenderer renderer, double belief, String... args) {
		return verbalizeIdeaWithBelief(renderer, belief, args);
	}

	// Override me!
	public String verbalizeIdeaAsNP(ConstantRenderer renderer, String... args) {
		return verbalizeIdea(renderer, args);
	}

	// Override me!
	public List<String> retrieveArguments(ConstantRenderer renderer, String... args) {
		return Arrays.asList(args);
	}

	public String toAtomString(String... args) {
		return predSymbol + "(" + StringUtils.join(args, ", ") + ")";
	}

	@Override
	public String toString() {
		return predSymbol + "<" + arity + ">";
	}

}

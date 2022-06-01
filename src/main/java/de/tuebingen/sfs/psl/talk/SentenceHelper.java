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
package de.tuebingen.sfs.psl.talk;

import java.util.List;

import static de.tuebingen.sfs.psl.talk.TalkingRuleOrConstraint.escapeForURL;

public class SentenceHelper {

    public static void appendAnd(List<String> args, StringBuilder sb) {
        appendAnd(args, sb, true);
    }

    public static void appendAnd(List<String> args, StringBuilder sb, boolean url) {
        appendAnd(args, 0, args.size(), sb, url);
    }

    public static void appendAnd(List<String> args, int from, int to, StringBuilder sb, boolean url) {
        appendList(args, from, to, "and", sb, url);
    }

    public static void appendAnd(List<String> printableArgs, List<TalkingPredicate> printableTalkingPredicates,
                                 List<String[]> printablePredicateArgs, int from, int to, StringBuilder sb, boolean url,
                                 ConstantRenderer renderer) {
        appendList(printableArgs, printableTalkingPredicates, printablePredicateArgs, from, to, "and", sb, url,
                renderer);
    }

    public static void appendOr(List<String> args, StringBuilder sb) {
        appendOr(args, sb, true);
    }

    public static void appendOr(List<String> args, StringBuilder sb, boolean url) {
        appendOr(args, 0, args.size(), sb, url);
    }

    public static void appendOr(List<String> args, int from, int to, StringBuilder sb, boolean url) {
        appendList(args, from, to, "or", sb, url);
    }

    public static void appendOr(List<String> printableArgs, List<TalkingPredicate> printableTalkingPredicates,
                                List<String[]> printablePredicateArgs, int from, int to, StringBuilder sb, boolean url,
                                ConstantRenderer renderer) {
        appendList(printableArgs, printableTalkingPredicates, printablePredicateArgs, from, to, "or", sb, url,
                renderer);
    }


    protected static void appendList(List<String> args, int from, int to, String conj, StringBuilder sb, boolean url) {
        for (int i = from; i < to; i++) {
            if (url) sb.append("\\url{");
            sb.append(args.get(i));
            if (url) sb.append("}");
            if (i == args.size() - 2) sb.append(" ").append(conj).append(" ");
            else if (i != args.size() - 1) sb.append(", ");
        }
    }

    protected static void appendList(List<String> printableArgs, List<TalkingPredicate> printableTalkingPredicates,
                                     List<String[]> printablePredicateArgs, int from, int to, String conj,
                                     StringBuilder sb, boolean url, ConstantRenderer renderer) {
        for (int i = from; i < to; i++) {
            if (url) addURL(printableArgs, printableTalkingPredicates, printablePredicateArgs, i, sb, renderer);
            else sb.append(printableArgs.get(i));
            if (i == printableArgs.size() - 2) sb.append(" ").append(conj).append(" ");
            else if (i != printableArgs.size() - 1) sb.append(", ");
        }
    }

    protected static void addURL(List<String> printableArgs, List<TalkingPredicate> printableTalkingPredicates,
                                 List<String[]> printablePredicateArgs, int index, StringBuilder sb,
                                 ConstantRenderer renderer) {
        String predicateName = printableArgs.get(index);
        sb.append("\\url");
        if (printableTalkingPredicates != null && printablePredicateArgs != null) {
            String verbalization = printableTalkingPredicates.get(index)
                    .verbalizeIdeaAsNP(renderer, printablePredicateArgs.get(index));
            if (!predicateName.equals(verbalization)) {
                sb.append("[");
                sb.append(escapeForURL(verbalization));
                sb.append("]");
            }
        }
        sb.append("{").append(predicateName).append("}");
    }

    protected static void addSentenceWithURL(List<String> printableArgs,
                                             List<TalkingPredicate> printableTalkingPredicates,
                                             List<String[]> printablePredicateArgs, List<Double> printableBeliefValues,
                                             int index, StringBuilder sb, ConstantRenderer renderer) {
        String predicateName = printableArgs.get(index);
        sb.append("\\url");
        if (printableTalkingPredicates != null && printablePredicateArgs != null && printableBeliefValues != null) {
            // This requires having implemented subclasses of TalkingPredicate in order to actually look nice.
            String verbalization = printableTalkingPredicates.get(index)
                    .verbalizeIdeaAsSentence(renderer, printableBeliefValues.get(index),
                            printablePredicateArgs.get(index));
            if (!predicateName.equals(verbalization)) {
                sb.append("[");
                sb.append(escapeForURL(verbalization));
                sb.append("]");
            }
        }
        sb.append("{").append(predicateName).append("}");
    }

}

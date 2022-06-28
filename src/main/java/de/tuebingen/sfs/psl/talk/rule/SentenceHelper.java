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
package de.tuebingen.sfs.psl.talk.rule;

import de.tuebingen.sfs.psl.talk.ConstantRenderer;
import de.tuebingen.sfs.psl.talk.PrintableAtom;

import java.util.List;

import static de.tuebingen.sfs.psl.talk.rule.TalkingRuleOrConstraint.escapeForURL;

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

    public static void appendAnd(List<PrintableAtom> printableAtoms, StringBuilder sb, ConstantRenderer renderer) {
        appendAnd(printableAtoms, sb, true, renderer);
    }

    public static void appendAnd(List<PrintableAtom> printableAtoms, StringBuilder sb, boolean url,
                                 ConstantRenderer renderer) {
        appendAnd(printableAtoms, 0, printableAtoms.size(), sb, url, renderer);
    }

    public static void appendAnd(List<PrintableAtom> printableAtoms, int from, int to, StringBuilder sb, boolean url,
                                 ConstantRenderer renderer) {
        appendList(printableAtoms, from, to, "and", sb, url, renderer);
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

    public static void appendOr(List<PrintableAtom> printableAtoms, int from, int to, StringBuilder sb, boolean url,
                                ConstantRenderer renderer) {
        appendList(printableAtoms, from, to, "or", sb, url, renderer);
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

    protected static void appendList(List<PrintableAtom> printableAtoms, int from, int to, String conj,
                                     StringBuilder sb, boolean url, ConstantRenderer renderer) {
        for (int i = from; i < to; i++) {
            if (url) addNpWithUrl(printableAtoms, i, sb, renderer);
            else addNpWithoutUrl(printableAtoms, i, sb, renderer);
            if (i == printableAtoms.size() - 2) sb.append(" ").append(conj).append(" ");
            else if (i != printableAtoms.size() - 1) sb.append(", ");
        }
    }

    protected static void addNpWithUrl(List<PrintableAtom> printableAtoms, int index, StringBuilder sb,
                                       ConstantRenderer renderer) {
        addNpWithUrl(printableAtoms.get(index), sb, renderer);
    }

    protected static void addNpWithUrl(PrintableAtom atom, StringBuilder sb, ConstantRenderer renderer) {
        sb.append("\\url");
        if (atom.canTalk()) {
            String verbalization = atom.getPred().verbalizeIdeaAsNP(renderer, atom.getArgs());
            if (!atom.getAtom().equals(verbalization)) {
                sb.append("[");
                sb.append(escapeForURL(verbalization));
                sb.append("]");
            }
        }
        sb.append("{").append(atom.getAtom()).append("}");
    }

    protected static void addNpWithoutUrl(List<PrintableAtom> printableAtoms, int index, StringBuilder sb,
                                          ConstantRenderer renderer) {
        addNpWithoutUrl(printableAtoms.get(index), sb, renderer);
    }

    protected static void addNpWithoutUrl(PrintableAtom atom, StringBuilder sb, ConstantRenderer renderer) {
        if (atom.canTalk()) {
            sb.append(atom.getPred().verbalizeIdeaAsNP(renderer, atom.getArgs()));
        } else {
            sb.append(atom.getAtom());
        }
    }

    protected static void addSentenceWithUrl(List<PrintableAtom> printableAtoms, int index, StringBuilder sb,
                                             ConstantRenderer renderer) {
        PrintableAtom atom = printableAtoms.get(index);
        sb.append("\\url");
        if (atom.canTalk()) {
            // This requires having implemented subclasses of TalkingPredicate in order to actually look nice.
            String verbalization = atom.getPred().verbalizeIdeaAsSentence(renderer, atom.getBelief(), atom.getArgs());
            if (!atom.getAtom().equals(verbalization)) {
                sb.append("[");
                sb.append(escapeForURL(verbalization));
                sb.append("]");
            }
        }
        sb.append("{").append(atom.getAtom()).append("}");
    }

    protected static void addSentenceWithoutUrl(List<PrintableAtom> printableAtoms, int index, StringBuilder sb,
                                                ConstantRenderer renderer) {
        addSentenceWithoutUrl(printableAtoms.get(index), sb, renderer);
    }

    protected static void addSentenceWithoutUrl(PrintableAtom atom, StringBuilder sb, ConstantRenderer renderer) {
        if (atom.canTalk()) {
            sb.append(atom.getPred().verbalizeIdeaAsSentence(renderer, atom.getBelief(), atom.getArgs()));
        } else {
            sb.append(atom.getAtom());
        }
    }

}

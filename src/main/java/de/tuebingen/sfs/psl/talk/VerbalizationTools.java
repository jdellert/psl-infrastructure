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

public final class VerbalizationTools {

    public static void connectArguments(StringBuilder sb, String... args) {
        connectArguments(sb, "'", "'", args);
    }

    public static void connectArgumentsAsPhonemes(StringBuilder sb, String... args) {
        connectArguments(sb, "/", "/", args);
        // TODO change to:
        // connectArguments(sb, "\\[", "\\]", args);
    }

    public static void connectArgumentsItalics(StringBuilder sb, String... args) {
        connectArguments(sb, "'", "'", args);
        // TODO change to:
        // connectArguments(sb, "\\textit{", "}", args);
    }

    public static void connectArguments(StringBuilder sb, String beforeArg, String afterArg, String... args) {
        int arity = args.length;
        for (int i = 0; i < arity; i++) {
            sb.append(beforeArg);
            sb.append(args[i]);
            sb.append(afterArg);
            sb.append((i == arity - 2) ? " and " : ", ");
        }
        sb.delete(sb.length() - 2, sb.length());
    }

}

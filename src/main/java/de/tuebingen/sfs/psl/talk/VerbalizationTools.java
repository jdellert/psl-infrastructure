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

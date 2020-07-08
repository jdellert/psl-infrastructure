package de.tuebingen.sfs.psl.eval;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.util.data.Tuple;
import de.tuebingen.sfs.psl.util.log.InferenceLogger;

import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

public class ModelEvaluator {

    private PslProblem pslProblem;
    private GoldStandard gs;

    
    public ModelEvaluator(PslProblem pslProblem, GoldStandard gs){
    	this.pslProblem = pslProblem;
    	this.gs = gs;
    }

    public Evaluation evaluate() {
        return new Evaluation(pslProblem, gs);
    }


    public static class Evaluation {

        PslProblem pslProblem;
        GoldStandard gs;

        int[] inGS;
        double[] inGSSums;

        int[] notInGS;
        double[] notInGSSums;

        int[] topNotInGS;
        double[] topNotInGSSums;

        List<Map<Tuple, Double>> found;
        List<Map<Tuple, Double>> foundNotInGS;
        List<List<Map.Entry<Tuple, Double>>> topFoundNotInGS;
        List<Set<Arguments>> notFound;


        Evaluation(PslProblem pslProblem, GoldStandard gs) {
            this.pslProblem = pslProblem;
            this.gs = gs;
            PredicateEvaluationTemplate[] preds = gs.getPredicates();

            inGS = new int[preds.length];
            inGSSums = new double[preds.length];
            notInGS = new int[preds.length];
            notInGSSums = new double[preds.length];
            topNotInGS = new int[preds.length];
            topNotInGSSums = new double[preds.length];
            found = new ArrayList<>();
            foundNotInGS = new ArrayList<>();
            topFoundNotInGS = new ArrayList<>();
            notFound = new ArrayList<>();

            for (int p = 0; p < preds.length; p++) {
                PredicateEvaluationTemplate pred = preds[p];
                Map<Tuple, Double> foundHere = new TreeMap<>();
                Map<Tuple, Double> foundHereNotInGS = new TreeMap<>();
                Map<Tuple, Double> results;
                results = pslProblem.extractTableForPredicate(pred.getName());
                for (Map.Entry<Tuple, Double> entry : results.entrySet()) {
                    Tuple args = entry.getKey();
                    double belief = entry.getValue();
                    if (gs.check(pred, args)) {
                        inGS[p]++;
                        inGSSums[p] += belief;
                        foundHere.put(args, belief);
                    }
                    else if (!pred.isIgnoredAtom(args) && !gs.alreadyFound(pred, args)){
                        notInGS[p]++;
                        notInGSSums[p] += belief;
                        foundHereNotInGS.put(args, belief);
                    }
                }

                topNotInGS[p] = Math.min(foundHere.size(), foundHereNotInGS.size());
                List<Map.Entry<Tuple, Double>> topNotFound = foundHereNotInGS.entrySet()
                        .stream()
                        .sorted(new EntryValueComparator<>())
                        .skip(foundHereNotInGS.size() - topNotInGS[p])
                        .collect(Collectors.toList());
                Collections.reverse(topNotFound);
                topNotInGSSums[p] = topNotFound.stream()
                        .mapToDouble(Map.Entry::getValue)
                        .reduce(0.0, Double::sum);

                found.add(foundHere);
                foundNotInGS.add(foundHereNotInGS);
                topFoundNotInGS.add(topNotFound);
                notFound.add(gs.missingAtoms(pred));
            }

            gs.reset();
        }


        public void printEvaluation(InferenceLogger logger) {
            printEvaluation(logger.getLogStream());
        }

        public void printEvaluation(PrintStream pStream) {
            pStream.println();
            pStream.println("===========================");
            pStream.println("COMPARISON TO GOLD STANDARD");
            pStream.println("===========================");

            PredicateEvaluationTemplate[] preds = gs.getPredicates();

            for (int p = 0; p < preds.length; p++) {
                pStream.println("\nRESULTS FOR " + preds[p].getTemplateLabel().toUpperCase() + ":");

                if (inGS[p] + notInGS[p] == 0) {
                    pStream.println("\tNo " + preds[p].getName() + " atoms found.");
                    continue;
                }

                int nFound = inGS[p];
                int missing = notFound.get(p).size();
                printNumericEvaluation(pStream, nFound, missing, notInGS[p], topNotInGS[p],
                        inGSSums[p], notInGSSums[p], topNotInGSSums[p]);

                pStream.println("\tFound GS atoms:");
                if (found.get(p).isEmpty())
                    pStream.println("\t\tNone");
                for (Tuple args : found.get(p).keySet()) {
                    pStream.printf(Locale.ROOT, "\t\t%s(%s): %.3f\n",
                            preds[p].getName(), args.toString(), found.get(p).get(args));
                }

                pStream.println("\tMissing GS atoms:");
                if (notFound.get(p).isEmpty())
                    pStream.println("\t\tNone");
                for (Arguments args : notFound.get(p)) {
                    pStream.println("\t\t" + args.toString(preds[p]));
                }

                pStream.println("\tFound top " + topFoundNotInGS.get(p).size() + " non-GS atoms:");
                if (topFoundNotInGS.get(p).isEmpty())
                    pStream.println("\t\tNone");
                for (Map.Entry<Tuple, Double> entry : topFoundNotInGS.get(p)) {
                    pStream.printf(Locale.ROOT, "\t\t%s(%s): %.3f\n",
                            preds[p].getName(), entry.getKey().toString(), entry.getValue());
                }

                gs.additionalEvaluation(preds[p], new TreeMap<>(found.get(p)), new TreeMap<>(foundNotInGS.get(p)),
                        new TreeSet<>(notFound.get(p)), pslProblem, pStream);
            }
        }

        public static void printNumericEvaluation(PrintStream pStream,
                                                  int nFound, int nMissing, int nFoundButNotInGS, int nTopFoundButNotInGS,
                                                  double inGSSum, double notInGSSum, double topNotInGSSum) {
            int total = nFound + nMissing;
            pStream.printf(Locale.ROOT, "\tFound %d/%d GS atoms (%.3f%%).\n",
                    nFound, total, ((((double) nFound) / total) * 100));
            pStream.printf(Locale.ROOT, "\tFound %d atoms not in GS.\n",
                    nFoundButNotInGS);
            pStream.printf(Locale.ROOT, "\tAverage belief value of found GS atoms: %.3f\n",
                    (inGSSum / nFound));
            pStream.printf(Locale.ROOT, "\tAverage belief value of GS atoms: %.3f\n",
                    (inGSSum / total));
            pStream.printf(Locale.ROOT, "\tAverage belief value of found non-GS atoms: %.3f\n",
                    (notInGSSum / nFoundButNotInGS));
            pStream.printf(Locale.ROOT, "\tAverage belief value of top %d found non-GS atoms: %.3f\n",
                    nTopFoundButNotInGS, (topNotInGSSum / nTopFoundButNotInGS));
        }


        public void printTabularEvaluation(InferenceLogger logger) {
            printTabularEvaluation(logger.getLogStream());
        }

        public List<TabularEvaluationEntry> printTabularEvaluation(PrintStream pStream) {
            PredicateEvaluationTemplate[] preds = gs.getPredicates();
            List<TabularEvaluationEntry> eval = new ArrayList<>();

            pStream.println("\nTABULAR EVALUATION RESULTS:");
            for (int p = 0; p < preds.length; p++) {
                if (inGS[p] + notInGS[p] > 0) {
                    eval.addAll(printTabularEvaluation(pStream, preds[p].getTemplateLabel(),
                            inGS[p], notFound.get(p).size(), notInGS[p],
                            topNotInGS[p], inGSSums[p], notInGSSums[p], topNotInGSSums[p]));
                    eval.addAll(gs.additionalTabularEvaluation(preds[p], new TreeMap<>(found.get(p)), new TreeMap<>(foundNotInGS.get(p)),
                            new TreeSet<>(notFound.get(p)), pslProblem, pStream));
                }
            }

            return eval;
        }

        public static List<TabularEvaluationEntry> printTabularEvaluation(
                PrintStream pStream, String evalLabel,
                int nFound, int nMissing, int nFoundButNotInGS, int nTopFoundButNotInGS,
                double inGSSum, double notInGSSum, double topNotInGSSum) {
            int totalGS = nFound + nMissing;
            int totalFound = nFound + nFoundButNotInGS;
            double precision = ((double) nFound) / totalFound;
            double recall = ((double) nFound) / totalGS;
            double gsBelief = inGSSum / totalFound;
            double fgsBelief = inGSSum / nFound;
            double ngsBelief = notInGSSum / nFoundButNotInGS;
            double tngsBelief = topNotInGSSum / nTopFoundButNotInGS;
            double normFound = nFound * fgsBelief;
            double normFoundButNotInGS = nFoundButNotInGS * ngsBelief;
            double normPrecision = normFound / (normFound + normFoundButNotInGS);
            double normRecall = recall * fgsBelief;

            List<TabularEvaluationEntry> eval = new ArrayList<>();
            eval.add(new TabularEvaluationEntry(evalLabel, "GS found", nFound));
            eval.add(new TabularEvaluationEntry(evalLabel, "GS missing", nMissing));
            eval.add(new TabularEvaluationEntry(evalLabel, "non-GS found", nFoundButNotInGS));
            eval.add(new TabularEvaluationEntry(evalLabel, "precision", precision));
            eval.add(new TabularEvaluationEntry(evalLabel, "norm. precision", normPrecision));
            eval.add(new TabularEvaluationEntry(evalLabel, "recall", recall));
            eval.add(new TabularEvaluationEntry(evalLabel, "norm. recall", normRecall));
            eval.add(new TabularEvaluationEntry(evalLabel, "GS belief", gsBelief));
            eval.add(new TabularEvaluationEntry(evalLabel, "found GS belief", fgsBelief));
            eval.add(new TabularEvaluationEntry(evalLabel, "non-GS belief", ngsBelief));
            eval.add(new TabularEvaluationEntry(evalLabel, "top n non-GS belief", tngsBelief));

            for (TabularEvaluationEntry entry : eval)
                pStream.println(entry);

            return eval;
        }

    }

    public static class TabularEvaluationEntry {

        public final String evalLabel;
        public final String evalMeasure;
        public final double evalResult;

        public TabularEvaluationEntry(String evalLabel, String evalMeasure, double evalResult) {
            this.evalLabel = evalLabel;
            this.evalMeasure = evalMeasure;
            this.evalResult = evalResult;
        }

        @Override
        public String toString() {
            return evalLabel + "\t" + evalMeasure + "\t" + evalResult;
        }
    }

    public static class EntryValueComparator<K, V extends Comparable<V>> implements Comparator<Map.Entry<K, V>> {

        @Override
        public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
            return o1.getValue().compareTo(o2.getValue());
        }
    }
}

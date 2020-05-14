package de.tuebingen.sfs.psl.eval;

import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.util.data.Tuple;
import de.tuebingen.sfs.util.InferenceLogger;

import java.io.PrintStream;
import java.util.*;

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

        List<Map<Tuple, Double>> found;
        List<Map<Tuple, Double>> foundNotInGS;
        List<Set<Arguments>> notFound;


        Evaluation(PslProblem pslProblem, GoldStandard gs) {
            this.pslProblem = pslProblem;
            this.gs = gs;
            PredicateEvaluationTemplate[] preds = gs.getPredicates();

            inGS = new int[preds.length];
            inGSSums = new double[preds.length];
            notInGS = new int[preds.length];
            notInGSSums = new double[preds.length];
            found = new ArrayList<>();
            foundNotInGS = new ArrayList<>();
            notFound = new ArrayList<>();

            for (int p = 0; p < preds.length; p++) {
                PredicateEvaluationTemplate pred = preds[p];
                Map<Tuple, Double> foundHere = new TreeMap<>();
                Map<Tuple, Double> foundHereNotInGS = new TreeMap<>();
                Map<Tuple, Double> results;
                results = pslProblem.extractTableForPredicate(pred.getName());
                for (Map.Entry<Tuple, Double> entry : results.entrySet()) {
                    if (gs.contains(pred, entry.getKey())) {
                        inGS[p]++;
                        inGSSums[p] += entry.getValue();
                        foundHere.put(entry.getKey(), entry.getValue());
                    }
                    else if (!pred.isIgnoredAtom(entry.getKey())){
                        notInGS[p]++;
                        notInGSSums[p] += entry.getValue();
                        foundHereNotInGS.put(entry.getKey(), entry.getValue());
                    }
                }
                found.add(foundHere);
                foundNotInGS.add(foundHereNotInGS);
                notFound.add(gs.missingAtoms(pred, foundHere.keySet()));
            }
        }


        public void printEvaluation(InferenceLogger logger) {
            printEvaluation(logger.getLogStream());
        }

        public void printEvaluation(PrintStream pStream) {
            pStream.println("===========================");
            pStream.println("COMPARISON TO GOLD STANDARD");
            pStream.println("===========================\n");

            PredicateEvaluationTemplate[] preds = gs.getPredicates();

            for (int p = 0; p < preds.length; p++) {
                pStream.println("RESULTS FOR " + preds[p].getName().toUpperCase() + ":");

                int nFound = inGS[p];
                int missing = notFound.get(p).size();
                printNumericEvaluation(pStream, nFound, missing, notInGS[p], inGSSums[p], notInGSSums[p]);

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

                gs.additionalEvaluation(preds[p], found.get(p), foundNotInGS.get(p), notFound.get(p),
                        pslProblem, pStream);
            }
        }

        public static void printNumericEvaluation(PrintStream pStream, int nFound, int nMissing, int nFoundButNotInGS,
                                                  double inGSSum, double notInGSSum) {
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
        }

    }
}

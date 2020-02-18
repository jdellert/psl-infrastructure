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
        return new Evaluation();
    }


    public class Evaluation {

        int[] inGS;
        double[] inGSSums;

        int[] notInGS;
        double[] notInGSSums;

        List<Set<Tuple>> found;
        List<Set<Arguments>> notFound;


        Evaluation() {
            PredicateEvaluationTemplate[] preds = gs.getPredicates();

            inGS = new int[preds.length];
            inGSSums = new double[preds.length];
            notInGS = new int[preds.length];
            notInGSSums = new double[preds.length];
            found = new ArrayList<>();
            notFound = new ArrayList<>();

            for (int p = 0; p < preds.length; p++) {
                PredicateEvaluationTemplate pred = preds[p];
                Set<Tuple> foundHere = new TreeSet<>();
                Map<Tuple, Double> results;
                results = pslProblem.extractTableForPredicate(pred.getName());
                for (Map.Entry<Tuple, Double> entry : results.entrySet()) {
                    if (gs.contains(pred, entry.getKey())) {
                        inGS[p]++;
                        inGSSums[p] += entry.getValue();
                        foundHere.add(entry.getKey());
                    }
                    else if (!pred.isIgnoredAtom(entry.getKey())){
                        notInGS[p]++;
                        notInGSSums[p] += entry.getValue();
                    }
                }
                found.add(foundHere);
                notFound.add(gs.missingAtoms(pred, foundHere));
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
                int total = nFound + missing;
                pStream.printf("\tFound %d/%d GS atoms (%.3f%%).\n", nFound, total, ((((double) nFound) / total) * 100));
                pStream.printf("\tFound %d atoms not in GS.\n", notInGS[p]);
                pStream.printf("\tAverage belief value of found GS atoms: %.3f\n", (inGSSums[p] / nFound));
                pStream.printf("\tAverage belief value of GS atoms: %.3f\n", (inGSSums[p] / total));
                pStream.printf("\tAverage belief value of found non-GS atoms: %.3f\n", (notInGSSums[p] / notInGS[p]));
                pStream.println("\tFound GS atoms:");
                if (found.get(p).isEmpty())
                    pStream.println("\t\tNone");
                for (Tuple args : found.get(p)) {
                    pStream.println("\t\t" + preds[p].getName() + "(" + args.toString() + ")");
                }
                pStream.println("\tMissing GS atoms:");
                if (notFound.get(p).isEmpty())
                    pStream.println("\t\tNone");
                for (Arguments args : notFound.get(p)) {
                    pStream.println("\t\t" + args.toString(preds[p]));
                }
            }
        }

    }
}

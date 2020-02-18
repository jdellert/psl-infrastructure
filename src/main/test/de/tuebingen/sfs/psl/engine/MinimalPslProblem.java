package de.tuebingen.sfs.psl.engine;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.linqs.psl.model.rule.GroundRule;

import de.tuebingen.sfs.psl.engine.AtomTemplate;
import de.tuebingen.sfs.psl.engine.DatabaseManager;
import de.tuebingen.sfs.psl.engine.InferenceResult;
import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.engine.RagFilter;
import de.tuebingen.sfs.psl.engine.RuleAtomGraph;

public class MinimalPslProblem extends PslProblem{

	public MinimalPslProblem(DatabaseManager dbManager, String name) {
		super(dbManager, name);
	}

	@Override
	public void declarePredicates() {
		declarePredicate("antecedent", 1);
		declarePredicate("consequent", 1);
		declarePredicate("small", 1);
	}

	@Override
	public void pregenerateAtoms() {
		addObservation("antecedent", "a");
		addObservation("antecedent", "b");
		addTarget("consequent", "a");
		addTarget("consequent", "b");
		addTarget("small", "a");
		addTarget("small", "b");
		
//		addTarget("antecedent", 0.8, "a");
//		addTarget("antecedent", 0.8, "b");
//		addObservation("consequent", 0.3, "a");
//		addObservation("consequent", 0.3, "b");
//		addObservation("small", "a");
//		addObservation("small", "b");
	}

	@Override
	public void addInteractionRules() {
//		addRule("sum", "small(+X) <= 0.5 .");
//		addRule("prior", "~consequent(X) .");
//		addRule("aToC", "antecedent(X) -> consequent(X) .");
		addRule("sum", "small(+X) <= 0.5 .");
		addRule("prior", "1: ~consequent(X)");
		addRule("aToC", "2: antecedent(X) -> consequent(X)");
	}
	
	@Override
	public InferenceResult call() throws Exception{
		List<List<GroundRule>> groundRules = runInference(true);
		RuleAtomGraph.GROUNDING_OUTPUT = true;
		RuleAtomGraph.ATOM_VALUE_OUTPUT = true;
		Map<String, Double> valueMap = extractResult(false);
		RuleAtomGraph rag = new RuleAtomGraph(this, new RagFilter(valueMap), groundRules);
		return new InferenceResult(rag, valueMap);
	}

	@Override
	public Set<AtomTemplate> declareAtomsForCleanUp() {
		return null;
	}

}

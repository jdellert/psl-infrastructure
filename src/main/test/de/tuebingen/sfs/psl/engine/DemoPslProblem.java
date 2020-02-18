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

public class DemoPslProblem extends PslProblem {
	String openPredName;
	String closedPredName;

	// The openPredName and closedPredName are for testing purposes only!
	public DemoPslProblem(DatabaseManager dbManager, String name, String openPredName, String closedPredName) {
		super(dbManager, name);
		this.openPredName = openPredName;
		this.closedPredName = closedPredName;
		declareNamedDemoPredicates();
		pregenerateNamedDemoAtoms();
	}

	@Override
	public void declarePredicates() {
	}

	public void declareNamedDemoPredicates() {
		declarePredicate(openPredName, 2);
		declarePredicate(closedPredName, 2);
	}

	@Override
	public void pregenerateAtoms() {
	}

	public void pregenerateNamedDemoAtoms() {
		addTarget(openPredName, openPredName.substring(7), "aa");
		addTarget(openPredName, openPredName.substring(7), "bb");
		addTarget(openPredName, openPredName.substring(7), "cc");

		addObservation(closedPredName, "aa", "xx");
		addObservation(closedPredName, "aa", "yy");
		addObservation(closedPredName, "aa", "zz");
	}

	public void addDemoObservation(String arg1, String arg2) {
		addObservation(closedPredName, arg1, arg2);
	}

	@Override
	public void addInteractionRules() {
	}

	@Override
	public Set<AtomTemplate> declareAtomsForCleanUp() {
		return null;
	}

	@Override
	public InferenceResult call() throws Exception {
		addInteractionRules();
		List<List<GroundRule>> groundRules = runInference(true);
//		this.atoms.closeDatabase();
		RuleAtomGraph.GROUNDING_OUTPUT = true;
		RuleAtomGraph.ATOM_VALUE_OUTPUT = true;
		Map<String, Double> valueMap = extractResult();
		RuleAtomGraph rag = new RuleAtomGraph(this, new RagFilter(valueMap), groundRules);
		return new InferenceResult(rag, valueMap);
	}

}

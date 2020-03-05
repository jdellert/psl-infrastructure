package de.tuebingen.sfs.psl.engine;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.groovy.PSLModel;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.parser.ModelLoader;
import org.linqs.psl.parser.RulePartial;

import de.tuebingen.sfs.psl.talk.TalkingPredicate;
import de.tuebingen.sfs.psl.talk.TalkingRule;
import de.tuebingen.sfs.psl.util.data.Multimap;
import de.tuebingen.sfs.psl.util.data.RankingEntry;
import de.tuebingen.sfs.psl.util.data.Tuple;
import de.tuebingen.sfs.psl.util.data.Multimap.CollectionType;

public abstract class PslProblem implements Callable<InferenceResult> {
	public boolean RULE_OUTPUT = false;
	public boolean GROUNDING_OUTPUT = false;
	public boolean VERBOSE = false;

	private final String name;

	// Shared with the other PslProblems and the PartitionManager:
	private DatabaseManager dbManager;
	
	private String dbPath;
	private PSLModel model; // local, contains rules

    Map<Rule,String> ruleToName;
    Map<String,Rule> nameToRule;
    Map<String, TalkingRule> nameToTalkingRule;
    Map<String, TalkingPredicate> talkingPredicates;
    Set<String> closedPredicates;

	Set<AtomTemplate> observations;
	Set<AtomTemplate> targets;

	private boolean declareUserPrior;
	
	/**
	 * Get the DatabaseManager via
	 * 
	 * {@code ProblemManager problemManager = ProblemManager.defaultProblemManager();
	 *  problemManager.getDbManager()}
	 */
	public PslProblem(DatabaseManager dbManager, String name) {
		this(dbManager, name, false);
	}

	public PslProblem(DatabaseManager dbManager, String name, boolean declareUserPrior) {
		this.dbManager = dbManager;
		this.name = name;
		this.declareUserPrior = declareUserPrior;

		String suffix = System.getProperty("user.name") + "@" + getHostname();
		String baseDBPath = Config.getString("dbpath", System.getProperty("java.io.tmpdir"));
		dbPath = Paths.get(baseDBPath, this.getClass().getName() + "_" + suffix).toString();

		System.err.println("Basic setup...");
		basicSetup(true);

        System.err.println("Creating rule store...");
        ruleToName = new HashMap<>();
        nameToRule = new TreeMap<>();
        nameToTalkingRule = new TreeMap<>();

		System.err.println("Declaring predicates...");
		talkingPredicates = new TreeMap<>();
		closedPredicates = new HashSet<>();
		declarePredicates();

		System.err.println("Pregenerating atoms...");
		pregenerateAtoms();
	}

	public static String getHostname() {
		String hostname = "unknown";

		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException ex) {
			System.err.println("Hostname can not be resolved, using '" + hostname + "'.");
		}

		return hostname;
	}

	private void basicSetup(boolean clearDB) {
		if (dbManager == null){
			// Should only be the case for stand-alone models!
			RDBMSDataStore dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbPath, clearDB));
			dbManager = new DatabaseManager(dataStore);
		}

		model = new PSLModel(this, dbManager.getDataStore());

		observations = new HashSet<>();
		targets = new HashSet<>();
	}
	
	/*
	 * Methods to be implemented based on the actual PSL problem:
	 */

	public abstract void declarePredicates();

	public abstract void pregenerateAtoms();

	public abstract void addInteractionRules();

	// --- called by the partition manager:

	// Feel free to override this method. It has to include a call to runInference()
//	public abstract InferenceResult call() throws Exception;
	public InferenceResult call() throws Exception{		
		System.err.println("Adding interaction rules...");
		addInteractionRules();
		List<List<GroundRule>> groundRules = runInference(true);
		RuleAtomGraph.GROUNDING_OUTPUT = true;
		RuleAtomGraph.ATOM_VALUE_OUTPUT = true;
		RuleAtomGraph.GROUNDING_SCORE_OUTPUT = true;
		Map<String, Double> valueMap = extractResult();
		RuleAtomGraph rag = new RuleAtomGraph(this, new RagFilter(valueMap), groundRules);
		return new InferenceResult(rag, valueMap);
	}

	// atoms that should be deleted
	public abstract Set<AtomTemplate> declareAtomsForCleanUp();

	/**
	 * Returns the atoms whose values should be inferred by this PslProblem.
	 * This includes all new atoms declared via addTarget as well as
	 * all already existing atoms registered via registerExistingTarget.
	 * 
	 * Override this method only if you implement the relevant contents of the add/register methods.
	 * 
	 * @return
	 */
	public Set<AtomTemplate> reserveAtomsForWriting() {
		return new HashSet<>(targets);
	}

	/**
	 * Returns the atoms whose values are to remain fixed during inference.
	 * This includes all new atoms declared via addObservation as well as
	 * all already existing atoms registered via registerExistingObservation.
	 * 
	 * Override this method only if you implement the relevant contents of the add/register methods.
	 * 
	 * @return
	 */
	public Set<AtomTemplate> declareAtomsForReading() {
		return new HashSet<>(observations);
	}

	// --- END called by the partition manager

	/*
	 * Getters
	 */

	public String getName() {
		return name;
	}

	public DataStore getDataStore() {
		return dbManager.getDataStore();
	}

	public PSLModel getPslModel() {
	    return model;
    }
	
	public DatabaseManager getDbManager(){
		return dbManager;
	}

	public Set<String> getClosedPredicates() {
		return new HashSet<>(closedPredicates);
	}
	
	public Set<AtomTemplate> getAllAtoms() {
		Set<AtomTemplate> atomSet = reserveAtomsForWriting();
		atomSet.addAll(declareAtomsForReading());
		return atomSet;
	}
	
	public int getNumberOfAtoms(){
		// The DB manager knows about atom deletions.
		return dbManager.getNumberOfAtoms(name);
	}
	
	public int getNumberOfTargets(){
		// The DB manager knows about atom deletions.
		return dbManager.getNumberOfTargets(name, targets);
	}

	/**
	 * See also {@link #addRule(String, String)}
	 */

	public Map<String, TalkingRule> getTalkingRules() {
		return nameToTalkingRule;
	}

	/*
	 * The actual functionality
	 */

	/**
	 * Run inference to infer the unknown relationships. 
	 * The ProblemManager needs to call dbManager.openDatabase() (atoms.closeDatabase())
	 * before (after) this method is directly or indirectly (via call()) executed. 
	 */
	protected void runInference() {
		runInference(false);
	}

	/**
	 * Run inference to infer the unknown relationships. 
	 * The ProblemManager needs to call dbManager.openDatabase() (atoms.closeDatabase())
	 * before (after) this method is directly or indirectly (via call()) executed. 
	 */
	protected List<List<GroundRule>> runInference(boolean getGroundRules) {
		Database inferDB = dbManager.getDatabase(name);
		List<List<GroundRule>> groundRules = null;
		try {
			System.err.println("Start inference.");
			MPEInference mpe = new MPEInference(model, inferDB);
			mpe.inference();
			if (getGroundRules) {
				GroundRuleStore grs = mpe.getGroundRuleStore();
				groundRules = new ArrayList<>();
				for (Rule rule : listRules()) {
					List<GroundRule> groundRuleList = new ArrayList<>();
					for (GroundRule gr : grs.getGroundRules(rule))
						groundRuleList.add(gr);
					groundRules.add(groundRuleList);
				}
			}
			mpe.close();
		} catch (IllegalArgumentException e) {
			String message = e.getMessage();
			String missingAtomPrefix = "Can only call getAtom() on persisted RandomVariableAtoms using a PersistedAtomManager. Cannot access ";
			if (message.startsWith(missingAtomPrefix)) {
				System.err.println(
						"ERROR: rule references an undeclared atom " + message.substring(missingAtomPrefix.length()));
			}
			System.err.println(e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
		return groundRules;
	}

	protected void declareOpenPredicate(String name, int arity) {
		declareOpenPredicate(new TalkingPredicate(name, arity));
	}

	protected void declareOpenPredicate(TalkingPredicate pred) {
		declarePredicate(pred, false);
	}

	protected void declareClosedPredicate(String name, int arity) {
		declareClosedPredicate(new TalkingPredicate(name, arity));
	}

	protected void declareClosedPredicate(TalkingPredicate pred) {
		declarePredicate(pred, true);
	}

	private void declarePredicate(TalkingPredicate pred, boolean closed) {
		dbManager.declarePredicate(pred);
		talkingPredicates.put(pred.getSymbol(), pred);

		if (closed)
			closedPredicates.add(pred.getSymbol());

		// TODO circular. how can declareUserPrior be applied to an entire PslProblem instead of just a set of predicates anyway? (vbl)
//		if (declareUserPrior) {
//			declareUserPrior(name, arity);
//		}
	}

    public static String existentialAtomName(String predName) {
        return "X" + predName;
    }

    public static String systemPriorName(String predName) {
        return "V" + predName;
    }

	public static String userPriorName(String predName) {
		return "U" + predName;
	}

	private void declareUserPrior(String name, int arity) {
		String priorName = userPriorName(name);
		declareOpenPredicate(priorName, arity);
		StringBuilder priArgsB = new StringBuilder();
		for (int i = 0; i < arity; i++)
			priArgsB.append('V').append(i).append(',');
		priArgsB.deleteCharAt(priArgsB.length() - 1);
		String priArgs = priArgsB.toString();
		addRule(name + "UserPrior", priorName + "(" + priArgs + ") -> " + name + "(" + priArgs + ") .");
	}

	// Should only be used internally. Actual PslProblem instances should call addObservation/addTarget!
	private void addAtom(String predName, String... tuple) {
		dbManager.addAtom(name, predName, tuple);
	}
	
	// Should only be used internally. Actual PslProblem instances should call addObservation/addTarget!
	private void addAtom(String predName, double value, String... tuple) {
		dbManager.addAtom(name, predName, value, tuple);
	}

	/**
	 * Adds an atom with a value of 1.0 that remains fixed throughout the inference process.
	 * @param predName
	 * @param tuple
	 */
	public void addObservation(String predName, String... tuple) {
		observations.add(new AtomTemplate(predName, tuple));
		addAtom(predName, tuple);
	}

	/**
	 * Adds an atom with a value that remains fixed throughout the inference process.
	 * @param predName
	 * @param value
	 * @param tuple
	 */
	public void addObservation(String predName, double value, String... tuple) {
		observations.add(new AtomTemplate(predName, tuple));
		addAtom(predName, value, tuple);
	}

	/**
	 * Adds an atom with a value that might be changed by the inference process.
	 * @param predName
	 * @param value
	 * @param tuple
	 */
	public void addTarget(String predName, double value, String... tuple) {
		targets.add(new AtomTemplate(predName, tuple));
		addAtom(predName, value, tuple);
	}

	/**
	 * Adds an atom whose value will be inferred during the inference process.
	 * @param predName
	 * @param tuple
	 */
	public void addTarget(String predName, String... tuple) {
		targets.add(new AtomTemplate(predName, tuple));
		addAtom(predName, tuple);
	}
	
	public void addUserPrior(String predName, double value, String... tuple) {
		addObservation(userPriorName(predName), value, tuple);
	}

	public void registerExistingTarget(AtomTemplate atom){
		targets.add(atom);
		dbManager.associateAtomWithProblem(name, atom);
	}
	
	public void registerExistingObservation(AtomTemplate atom){
		observations.add(atom);
		dbManager.associateAtomWithProblem(name, atom);
	}
	
	/**
	 * Marks the matching open atoms as closed. 
	 * Actually changing their partitions requires preparing a new inference via the PartitionManager 
	 * or calling dbManager.moveToPartition.
	 * @param predName
	 */
	public void fixateAtoms(String predName){
		// If this is very slow, it might be worthwhile to check
		// whether it's possible to get the relevant partitions 
		// to move all atoms for this predicate 
		List<AtomTemplate> matches = new ArrayList<>();
		for (AtomTemplate openAtom : targets){
			if (predName.equals(openAtom.getPredicateName())){
				matches.add(openAtom);
				observations.add(openAtom);
			}
		}
		targets.removeAll(matches);
		// The actual partition update happens the next time the PartitionManager prepares a new inference.
	}
	
	/**
	 * Marks the matching open atoms as closed and updates their value. 
	 * Actually changing their partitions requires preparing a new inference via the PartitionManager 
	 * or calling dbManager.moveToPartition.
	 * @param value
	 * @param predName
	 * @param args
	 */
	public void fixateAtomsToValue(double value, String predName, String... args) {
		dbManager.setAtomsToValue(value, predName, args);
		AtomTemplate atom = new AtomTemplate(predName, args);
		List<AtomTemplate> matches = new ArrayList<>();
		for (AtomTemplate openAtom : targets){
			if (atom.equalsWithWildcards(openAtom)){
				matches.add(openAtom);
				observations.add(openAtom);
			}
		}
		targets.removeAll(matches);
	}
	
	/**
	 * Marks the matching closed atoms as open and updates their value. 
	 * Actually changing their partitions requires preparing a new inference via the PartitionManager 
	 * or calling dbManager.moveToPartition.
	 * @param predName
	 * @param args
	 */
	public void releaseAtoms(String predName, String... args) {
		AtomTemplate atom = new AtomTemplate(predName, args);
		List<AtomTemplate> matches = new ArrayList<>();
		for (AtomTemplate closedAtom : observations){
			if (atom.equalsWithWildcards(closedAtom)){
				matches.add(closedAtom);
				targets.add(closedAtom);
			}
		}
		observations.removeAll(matches);
	}
	
    public void addRule(TalkingRule rule) {
        String ruleName = rule.getName();
        if (nameToRule.containsKey(ruleName)) {
            System.err.println("Rule '" + ruleName + "' already added to this model. Ignoring second declaration. "
                    +"Please make sure to give your rules unique names.");
            return;
        }
        nameToTalkingRule.put(ruleName, rule);
        nameToRule.put(ruleName, rule.getRule());
        ruleToName.put(rule.getRule(), ruleName);
        model.addRule(rule.getRule());
    }

    public void addRule(String ruleName, String ruleString) {
        if (nameToRule.containsKey(ruleName)) {
            System.err.println("Rule '" + ruleName + "' already added to this model. Ignoring second declaration. "
                    +"Please make sure to give your rules unique names.");
            return;
        }
        try {
            RulePartial partial = ModelLoader.loadRulePartial(dbManager.getDataStore(), ruleString);
            Rule rule = partial.toRule();

            ruleToName.put(rule, ruleName);
            nameToRule.put(ruleName, rule);
            nameToTalkingRule.put(ruleName, TalkingRule.createTalkingRule(ruleName, ruleString, rule, this));

            model.addRule(rule);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void removeRule(String ruleName) {
        if (nameToRule.containsKey(ruleName)) {
            Rule rule = nameToRule.get(ruleName);
            model.removeRule(rule);
            nameToRule.remove(ruleName);
            ruleToName.remove(rule);
        }
    }

    public int nOfRules() {
        return nameToRule.size();
    }

    public List<Rule> listRules() {
        List<Rule> ruleList = new LinkedList<Rule>();
        for (Rule rule : model.getRules()) {
            ruleList.add(rule);
        }
        return ruleList;
    }

    public String getNameForRule(Rule rule) {
        return ruleToName.get(rule);
    }

    public Rule getRuleByName(String ruleName) {
        return nameToRule.get(ruleName);
    }

	public Map<String, Double> extractResult() {
		return extractResult(null);
	}

	public Map<String, Double> extractResult(PrintStream print) {
		return extractResult(print, true);
	}

	public Map<String, Double> extractResult(boolean onlyOpen) {
		return extractResult(null, onlyOpen);
	}

	// TODO make these methods protected. should only be used in call(), later on you can use the InferenceResult (vbl)
	public Map<String, Double> extractResult(PrintStream print, boolean onlyOpen) {
		Set<String> predicates = talkingPredicates.keySet();
		if (onlyOpen)
			predicates.removeAll(closedPredicates);
		Multimap<String, RankingEntry<AtomTemplate>> predicatesToAtoms = dbManager.getAtomValuesByPredicate(getName(), predicates);
		Map<String, Double> atomToValue = new TreeMap<>();
		for (String predicate : predicatesToAtoms.keySet()){
			for (RankingEntry<AtomTemplate> rankingEntry : predicatesToAtoms.getList(predicate)){
				atomToValue.put(rankingEntry.key.toString(), rankingEntry.value);
			}
		}
		return atomToValue;
	}

	public Map<Tuple, Double> extractTableForPredicate(String pred) {
		return dbManager.getAllWithValueForProblem(pred, getName());
	}

	public void printResult() {
		printResult(System.out);
	}
	 
	// TODO how does this differ from InferenceResult.printInferenceValues(); ? (vbl)
	public void printResult(PrintStream printStream) {
		Set<String> predicates = talkingPredicates.keySet();
		predicates.removeAll(closedPredicates);
		dbManager.printWithValue(getName(), predicates, printStream);
	}
	
	public void printAtomsToConsole() {
		dbManager.print(getName(), talkingPredicates.keySet(), System.out);
	}

	public Map<String, TalkingPredicate> getTalkingPredicates() {
		return talkingPredicates;
	}

	public Multimap<String, Tuple> getTuplesByPredicate(){
		Multimap<String, Tuple> map = new Multimap<>(CollectionType.SET);
		for (AtomTemplate atom : getAllAtoms()) {
			map.put(atom.getPredicateName(), atom.getArgTuple());
		}
		return map;
	}
	
	public String toString(){
		return "PslProblem[" + name + "]";
	}
}

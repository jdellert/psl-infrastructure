package de.tuebingen.sfs.psl.engine;

import de.tuebingen.sfs.psl.talk.TalkingPredicate;
import de.tuebingen.sfs.psl.talk.TalkingRule;
import de.tuebingen.sfs.psl.util.data.Multimap;
import de.tuebingen.sfs.psl.util.data.Multimap.CollectionType;
import de.tuebingen.sfs.psl.util.data.RankingEntry;
import de.tuebingen.sfs.psl.util.data.Tuple;
import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.groovy.PSLModel;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.AbstractGroundArithmeticRule;
import org.linqs.psl.model.rule.logical.AbstractGroundLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.parser.ModelLoader;
import org.linqs.psl.parser.RulePartial;

import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;

public abstract class PslProblem implements Callable<InferenceResult> {
    public static final String EXISTENTIAL_PREFIX = "X";
    public static final String SYSTEM_PRIOR_PREFIX = "V";
    public static final String USER_PRIOR_PREFIX = "U";
    private final String name;
    public boolean RULE_OUTPUT = false;
    public boolean GROUNDING_OUTPUT = false;
    public boolean VERBOSE = false;
    Map<Rule, String> ruleToName;
    Map<String, Rule> nameToRule;
    Map<String, TalkingRule> nameToTalkingRule;
    Map<String, TalkingPredicate> talkingPredicates;
    Set<String> closedPredicates;
    private PslProblemConfig config;
    // Shared with the other PslProblems and the PartitionManager:
    private DatabaseManager dbManager;
    private String dbPath;
    private PSLModel model; // local, contains rules
    private boolean declareUserPrior;

    /**
     * Get the DatabaseManager via
     * <p>
     * {@code ProblemManager problemManager = ProblemManager.defaultProblemManager();
     * problemManager.getDbManager()}
     */
    public PslProblem(DatabaseManager dbManager, String name) {
        this(dbManager, name, false);
    }

    public PslProblem(DatabaseManager dbManager, String name, boolean declareUserPrior) {
        this(new PslProblemConfig(name, declareUserPrior, dbManager));
    }

    public PslProblem(PslProblemConfig config) {
        this.config = config;
        this.dbManager = config.getDbManager();
        this.name = config.getName();
        this.declareUserPrior = config.isDeclareUserPrior();

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

    public static String existentialAtomName(String predName) {
        return EXISTENTIAL_PREFIX + predName;
    }

    /*
     * Methods to be implemented based on the actual PSL problem:
     */

    public static String systemPriorName(String predName) {
        return SYSTEM_PRIOR_PREFIX + predName;
    }

    public static String userPriorName(String predName) {
        return USER_PRIOR_PREFIX + predName;
    }

    public static String predicatePrefix(String predName) {
        if (predName.startsWith(EXISTENTIAL_PREFIX)) {
            return EXISTENTIAL_PREFIX;
        }
        if (predName.startsWith(SYSTEM_PRIOR_PREFIX)) {
            return SYSTEM_PRIOR_PREFIX;
        }
        if (predName.startsWith(USER_PRIOR_PREFIX)) {
            return USER_PRIOR_PREFIX;
        }
        return "";
    }

    // --- called by the partition manager:

    private void basicSetup(boolean clearDB) {
        if (dbManager == null) {
            // Should only be the case for stand-alone models!
            RDBMSDataStore dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbPath, clearDB));
            dbManager = new DatabaseManager(dataStore);
        }

        model = new PSLModel(this, dbManager.getDataStore());
    }

    public abstract void declarePredicates();

    public abstract void pregenerateAtoms();

    public abstract void addInteractionRules();

    // Feel free to override this method. It has to include a call to runInference()
//	public abstract InferenceResult call() throws Exception;
    public InferenceResult call() throws Exception {
        System.err.println("Adding interaction rules...");
        addInteractionRules();
        List<List<GroundRule>> groundRules = runInference(true);
        RuleAtomGraph.GROUNDING_OUTPUT = true;
        RuleAtomGraph.ATOM_VALUE_OUTPUT = true;
        RuleAtomGraph.GROUNDING_SCORE_OUTPUT = true;
        Map<String, Double> valueMap = extractResultsForAllPredicates();
        RuleAtomGraph rag = new RuleAtomGraph(this, new RagFilter(valueMap), groundRules);
        return new InferenceResult(rag, valueMap);
    }

    // --- END called by the partition manager

    /*
     * Getters
     */

    // atoms that should be deleted
    public abstract Set<AtomTemplate> declareAtomsForCleanUp();

    /**
     * Returns the atoms whose values should be inferred by this PslProblem.
     * This includes all new atoms declared via addTarget as well as
     * all already existing atoms registered via registerExistingTarget.
     * <p>
     * Override this method only if you implement the relevant contents of the add/register methods.
     *
     * @return
     */
    public Set<AtomTemplate> reserveAtomsForWriting() {
        return getAtomsMarkedAs(true);
    }

    /**
     * Returns the atoms whose values are to remain fixed during inference.
     * This includes all new atoms declared via addObservation as well as
     * all already existing atoms registered via registerExistingObservation.
     * <p>
     * Override this method only if you implement the relevant contents of the add/register methods.
     *
     * @return
     */
    public Set<AtomTemplate> declareAtomsForReading() {
        return getAtomsMarkedAs(false);
    }

    private Set<AtomTemplate> getAtomsMarkedAs(boolean target) {
        Set<AtomTemplate> atoms = new HashSet<>();
        for (String predName : talkingPredicates.keySet()) {
            List<Tuple> predAtoms;
            if (target) {
                predAtoms = dbManager.getAllTargetsForProblem(predName, name);
            } else {
                predAtoms = dbManager.getAllObservationsForProblem(predName, name);
            }
            for (Tuple args : predAtoms) {
                atoms.add(new AtomTemplate(predName, args.toList()));
            }
        }
        return atoms;
    }

    public PslProblemConfig getConfig() {
        return config;
    }

    public String getName() {
        return name;
    }

    public DataStore getDataStore() {
        return dbManager.getDataStore();
    }

    public PSLModel getPslModel() {
        return model;
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    /*
     * The actual functionality
     */

    public Set<String> getClosedPredicates() {
        return new HashSet<>(closedPredicates);
    }

    public int getNumberOfAtoms() {
        // The DB manager knows about atom deletions.
        return dbManager.getNumberOfAtoms(name);
    }

    public int getNumberOfTargets() {
        // The DB manager knows about atom deletions.
        return dbManager.getNumberOfTargets(name);
    }

    /**
     * See also {@link #addRule(String, String)}
     */

    public Map<String, TalkingRule> getTalkingRules() {
        return nameToTalkingRule;
    }

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

    protected void setPredicateClosed(String name) {
        if (talkingPredicates.containsKey(name)) {
            closedPredicates.add(name);
            fixateAtoms(name);
        } else
            System.err.println("Tried to close unknown predicate \"" + name + "\".");
    }

    protected void setPredicateOpen(String name) {
        closedPredicates.remove(name);
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
    private void addAtom(boolean isTarget, String predName, String... tuple) {
        dbManager.addAtom(name, isTarget, predName, tuple);
    }

    // Should only be used internally. Actual PslProblem instances should call addObservation/addTarget!
    private void addAtom(boolean isTarget, String predName, double value, String... tuple) {
        dbManager.addAtom(name, isTarget, predName, value, tuple);
    }

    /**
     * Adds an atom with a value of 1.0 that remains fixed throughout the inference process.
     *
     * @param predName
     * @param tuple
     */
    public void addObservation(String predName, String... tuple) {
        addAtom(false, predName, tuple);
    }

    /**
     * Adds an atom with a value that remains fixed throughout the inference process.
     *
     * @param predName
     * @param value
     * @param tuple
     */
    public void addObservation(String predName, double value, String... tuple) {
        addAtom(false, predName, value, tuple);
    }

    /**
     * Adds an atom with a value that might be changed by the inference process.
     *
     * @param predName
     * @param value
     * @param tuple
     */
    public void addTarget(String predName, double value, String... tuple) {
        addAtom(true, predName, value, tuple);
    }

    /**
     * Adds an atom whose value will be inferred during the inference process.
     *
     * @param predName
     * @param tuple
     */
    public void addTarget(String predName, String... tuple) {
        addAtom(true, predName, tuple);
    }

    public void addUserPrior(String predName, double value, String... tuple) {
        addObservation(userPriorName(predName), value, tuple);
    }

    public void registerExistingTarget(AtomTemplate atom) {
        dbManager.associateAtomWithProblem(name, true, atom);
    }

    public void registerExistingObservation(AtomTemplate atom) {
        dbManager.associateAtomWithProblem(name, false, atom);
    }

    /**
     * Marks the matching open atoms as closed.
     * Actually changing their partitions requires preparing a new inference via the PartitionManager
     * or calling dbManager.moveToPartition.
     *
     * @param predName
     */
    public void fixateAtoms(String predName) {
        dbManager.setAtomsAsObservation(predName, name);
    }

    /**
     * Marks the matching open atoms as closed and updates their value.
     * Actually changing their partitions requires preparing a new inference via the PartitionManager
     * or calling dbManager.moveToPartition.
     *
     * @param value
     * @param predName
     * @param args
     */
    public void fixateAtomsToValue(double value, String predName, String... args) {
        dbManager.setAtomsToValueForProblem(getName(), predName, new AtomTemplate(predName, args), value);
        dbManager.setAtomsAsObservation(predName, name, new AtomTemplate(predName, args));
    }

    /**
     * Marks the matching closed atoms as open and updates their value.
     * Actually changing their partitions requires preparing a new inference via the PartitionManager
     * or calling dbManager.moveToPartition.
     *
     * @param predName
     * @param args
     */
    public void releaseAtoms(String predName, String... args) {
        dbManager.setAtomsAsTarget(predName, name, new AtomTemplate(predName, args));
    }

    public void addRule(TalkingRule rule) {
        String ruleName = rule.getName();
        if (nameToRule.containsKey(ruleName)) {
            System.err.println("Rule '" + ruleName + "' already added to this model. Ignoring second declaration. "
                    + "Please make sure to give your rules unique names.");
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
                    + "Please make sure to give your rules unique names.");
            return;
        }
        try {
            RulePartial partial = ModelLoader.loadRulePartial(dbManager.getDataStore(), ruleString);
            Rule rule = partial.toRule();

            ruleToName.put(rule, ruleName);
            nameToRule.put(ruleName, rule);
            nameToTalkingRule.put(ruleName, TalkingRule.createTalkingRule(ruleName, ruleString, rule, this));

            model.addRule(rule);
        } catch (Exception e) {
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

    protected Map<String, Double> extractResultsForAllPredicates() {
        return extractResultsForAllPredicates(null);
    }

    protected Map<String, Double> extractResultsForAllPredicates(PrintStream print) {
        return extractResultsForAllPredicates(print, false);
    }

    protected Map<String, Double> extractResultsForAllPredicates(boolean onlyOpen) {
        return extractResultsForAllPredicates(null, onlyOpen);
    }

    protected Map<String, Double> extractResultsForAllPredicates(PrintStream print, boolean onlyOpen) {
        Set<String> predicates = new HashSet<>();
        predicates.addAll(talkingPredicates.keySet());
        if (onlyOpen)
            predicates.removeAll(closedPredicates);
        Multimap<String, RankingEntry<AtomTemplate>> predicatesToAtoms = dbManager.getAtomValuesByPredicate(getName(),
                predicates);
        Map<String, Double> atomToValue = new TreeMap<>();
        for (String predicate : predicatesToAtoms.keySet()) {
            for (RankingEntry<AtomTemplate> rankingEntry : predicatesToAtoms.getList(predicate)) {
                atomToValue.put(rankingEntry.key.toString(), rankingEntry.value);
                if (print != null)
                    print.println("Extracted " + rankingEntry.key + " " + rankingEntry.value);
            }
        }
        return atomToValue;
    }

    protected Map<String, Double> extractResultsForGroundRules(List<List<GroundRule>> groundRules, PrintStream print) {
        Multimap<String, AtomTemplate> predsToAtoms = new Multimap<>(CollectionType.SET);
        for (int i = 0; i < groundRules.size(); i++) {
            for (GroundRule groundRule : groundRules.get(i)) {
                List<GroundAtom> groundAtoms = new LinkedList<GroundAtom>();
                if (groundRule instanceof AbstractGroundArithmeticRule) {
                    groundAtoms = AbstractGroundArithmeticRuleAccess
                            .extractAtoms((AbstractGroundArithmeticRule) groundRule);
                } else if (groundRule instanceof AbstractGroundLogicalRule) {
                    groundAtoms = AbstractGroundLogicalRuleAccess.extractAtoms((AbstractGroundLogicalRule) groundRule);
                }
                for (GroundAtom atom : groundAtoms) {
                    String pred = TalkingPredicate.getPredNameFromAllCaps(atom.getPredicate().getName());
                    Constant[] args = atom.getArguments();
                    String[] stringArgs = new String[args.length];
                    for (int c = 0; c < args.length; c++) {
                        String arg = args[c].toString();
                        // Each Constant is surrounded by '...'
                        arg = arg.substring(1, arg.length() - 1);
                        stringArgs[c] = arg;
                    }
                    predsToAtoms.put(pred, new AtomTemplate(pred, stringArgs));
                }
            }
        }
        Map<String, Double> results = new TreeMap<>();
        for (Entry<String, Collection<AtomTemplate>> pred : predsToAtoms.entrySet()) {
            List<RankingEntry<AtomTemplate>> atoms = dbManager.getAtoms(pred.getKey(),
                    pred.getValue().toArray(new AtomTemplate[pred.getValue().size()]));
            for (RankingEntry<AtomTemplate> atom : atoms) {
                results.put(atom.key.toString(), atom.value);
                if (print != null)
                    print.println("Extracted " + atom.key + " " + atom.value);
            }
        }
        return results;
    }

    public Map<Tuple, Double> extractTableForPredicate(String pred) {
        return dbManager.getAllWithValueForProblem(pred, getName());
    }

    public void printResult() {
        printResult(System.out);
    }

    // TODO how does this differ from InferenceResult.printInferenceValues(); ? (vbl)
    public void printResult(PrintStream printStream) {
        Set<String> predicates = new HashSet<>();
        predicates.addAll(talkingPredicates.keySet());
        predicates.removeAll(closedPredicates);
        dbManager.printWithValue(getName(), predicates, printStream);
    }

    public void printRules(PrintStream out) {
        for (Entry<String, TalkingRule> rule : nameToTalkingRule.entrySet()) {
            out.println(rule.getKey() + "\t" + rule.getValue().getRuleString());
        }
    }

    public void printAtomsToConsole() {
        dbManager.print(getName(), talkingPredicates.keySet(), System.out);
    }

    public Map<String, TalkingPredicate> getTalkingPredicates() {
        return talkingPredicates;
    }

    public Multimap<String, Tuple> getTuplesByPredicate() {
        Multimap<String, Tuple> map = new Multimap<>(CollectionType.SET);
        for (String predName : talkingPredicates.keySet()) {
            map.putAll(predName, dbManager.getAllForProblem(predName, name));
        }
        return map;
    }

    public String toString() {
        return "PslProblem[" + name + "]";
    }
}

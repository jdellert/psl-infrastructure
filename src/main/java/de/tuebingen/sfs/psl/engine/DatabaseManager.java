package de.tuebingen.sfs.psl.engine;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import org.h2.api.Trigger;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.PredicateInfo;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.ConstantType;

import de.tuebingen.sfs.psl.talk.TalkingPredicate;
import de.tuebingen.sfs.psl.util.data.Multimap;
import de.tuebingen.sfs.psl.util.data.RankingEntry;
import de.tuebingen.sfs.psl.util.data.StringUtils;
import de.tuebingen.sfs.psl.util.data.Trie;
import de.tuebingen.sfs.psl.util.data.Tuple;
import de.tuebingen.sfs.psl.util.data.Multimap.CollectionType;

public class DatabaseManager {

	// Table and columns names:
	public static final String PROBLEMS2ATOMS_TABLE = "problems_to_atoms";
	private static final String P2A_SHORT = "prob";
	private static final String PRED_SHORT = "pred";
	private static final String AUX_SHORT = "aux";
	public static final String PROBLEM_ID_COLUMN_NAME = "problem_id";
	public static final String PREDICATE_NAME_COLUMN_NAME = "predicate";
	public static final String ATOM_ID_COLUMN_NAME = "atom_id";

	// Stuff:
	public static final double DEFAULT_BELIEF = 1.0;

	// TODO from ModelStorePSL: replace with UniqueIntID
	private static final ConstantType ARG_TYPE = ConstantType.UniqueStringID;

	private final RDBMSDataStore dataStore;
	private Map<String, TalkingPredicate> talkingPredicates;
	private Map<String, StandardPredicate> predicates;
	private Multimap<String, AtomTemplate> problemsToAtoms; // TODO: Remove once unused

	private Map<String, Database> problemsToDatabases;
	private int nextId = 0;

	private Partition stdPartition;

	private int atomsTotal;
	private long startMemory;

	private Trie<String> blacklist;

	public DatabaseManager(RDBMSDataStore dataStore) {
		this.dataStore = dataStore;
		predicates = new TreeMap<>();
		talkingPredicates = new TreeMap<>();
		problemsToAtoms = new Multimap<>(CollectionType.SET);
		problemsToDatabases = new HashMap<>();
		stdPartition = dataStore.getPartition(PartitionManager.STD_PARTITION_ID);
		atomsTotal = 0;
		Runtime runtime = Runtime.getRuntime();
		runtime.gc();
		startMemory = runtime.totalMemory() - runtime.freeMemory();
		blacklist = new Trie<>();

		try (Connection conn = dataStore.getConnection()) {
			String stmt = "CREATE TABLE " + PROBLEMS2ATOMS_TABLE + " ("
					+ PROBLEM_ID_COLUMN_NAME + " VARCHAR(" + ProblemManager.MAX_PROBLEM_ID_LENGTH + ") NOT NULL, "
					+ PREDICATE_NAME_COLUMN_NAME + " TEXT NOT NULL, "
					+ ATOM_ID_COLUMN_NAME + " INT NOT NULL,"
					+ "PRIMARY KEY (" + PROBLEM_ID_COLUMN_NAME + ", " + ATOM_ID_COLUMN_NAME + "));";
			PreparedStatement prepStmt = conn.prepareStatement(stmt);
			prepStmt.execute();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	////////////////
	// DEPRECATED //
	////////////////

	// Method body contains new idioms, so replace calls to these methods by method body!

	// TODO: Remove once unused
	@Deprecated
	public Set<Tuple> getTuplesForPredicateAboveThreshold(String problemId, String predName, double threshold) {
		return new HashSet<>(getAllAboveThresholdForProblem(predName, problemId, threshold));
	}

	// TODO: Remove once unused
	@Deprecated
	public Set<Tuple> getTuplesForPredicateBelowThreshold(String problemId, String predName, double threshold) {
		return new HashSet<>(getAllBelowThresholdForProblem(predName, problemId, threshold));
	}

	// TODO: Remove once unused
	@Deprecated
	public Set<Tuple> getTuplesForPredicate(String problemId, String predName) {
		return new HashSet<>(getAllForProblem(predName, problemId));
	}


	////////////////////
	// SIMPLE GETTERS //
	////////////////////

	public RDBMSDataStore getDataStore() {
		return dataStore;
	}

	public Database getDatabase(String problemId){
		return problemsToDatabases.get(problemId);
	}

	public StandardPredicate getPredicateByName(String predicate) {
		return predicates.get(predicate);
	}

	public Map<String, TalkingPredicate> getTalkingPredicates() {
		return talkingPredicates;
	}
	
	public int getNumberOfAtoms(String problemId){
		try (Connection conn = dataStore.getConnection()) {
			String stmt = "SELECT count(*) FROM " + PROBLEMS2ATOMS_TABLE
					+ " WHERE " + PROBLEM_ID_COLUMN_NAME + " = ?;";
			PreparedStatement prepStmt = conn.prepareStatement(stmt);
			prepStmt.setString(1, problemId);
			ResultSet res = prepStmt.executeQuery();
			if (res.next())
				return res.getInt(1);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return 0;
	}

	// TODO: How to implement? Mark target status in problems2atoms DB?
	public int getNumberOfTargets(String problemId, Set<AtomTemplate> originalTargets) {
		int count = 0;
		Collection<AtomTemplate> currentAtoms = problemsToAtoms.get(problemId);
		for (AtomTemplate originalTarget : originalTargets){
			if (currentAtoms.contains(originalTarget)){
				// = if the atom hasn't been deleted
				count++;
			}
		}
		return count;
	}

	public boolean existsAtom(String predName, String... args) {
		Predicate pred = getPredicateByName(predName);
		if (pred != null) {
			PredicateInfo predInfo = new PredicateInfo(pred);
			AtomTemplate atom = new AtomTemplate(predName, args);

			String stmt = "SELECT 1 FROM " + predInfo.tableName() + " " + PRED_SHORT
					+ new WhereStatement().matchAtoms(atom) + ";";
			return runQueryResultNotEmpty(stmt, atom);
		}
		return false;
	}

	public boolean isAtomOwnedByProblem(String problemId, String predName, String... args) {
		Predicate pred = getPredicateByName(predName);
		if (pred != null) {
			PredicateInfo predInfo = new PredicateInfo(pred);
			AtomTemplate atom = new AtomTemplate(predName, args);

			String stmt = "SELECT 1 FROM " + PROBLEMS2ATOMS_TABLE + " " + P2A_SHORT + ", "
					+ predInfo.tableName() + " " + PRED_SHORT
					+ new WhereStatement().ownedByProblem(problemId).matchAtoms(atom) + ";";
			return runQueryResultNotEmpty(stmt, atom);
		}
		return false;
	}

	/**
	 * Should only be used BEFORE inferences are run.
	 */
	public void openDatabase(String problemId, Partition write, Set<String> closedPredicates,
							 Partition... read) {
		System.err.println("Opening the database for " + problemId + " (write=" + write + "; read=" + Arrays.toString(read) + ")");
		Database db = problemsToDatabases.get(problemId);
		if (db == null) {
			Set<StandardPredicate> toClose = closedPredicates.stream()
					.map(pName -> predicates.get(pName)).collect(Collectors.toSet());
			db = dataStore.getDatabase(write, toClose, read);
			problemsToDatabases.put(problemId, db);
		}
	}

	public void closeDatabase(String problemId) {
		Database db = problemsToDatabases.get(problemId);
		if (db != null) {
			db.close();
			db = null;
			problemsToDatabases.put(problemId, null);
		}
	}


	////////////////
	// DB GETTERS //
	////////////////

	public List<Tuple> getAll(String predName) {
		return getAllForProblem(predName, "");
	}

	public List<Tuple> getAllForProblem(String predName, String problemId) {
		return getAllWhere(predName, new WhereStatement().ownedByProblem(problemId));
	}

	public List<Tuple> getAllOrderedBy(String predName, int[] orderBy) {
		return getAllOrderedByForProblem(predName, "", orderBy);
	}

	public List<Tuple> getAllOrderedBy(String predName, int[] orderBy, boolean castToInt) {
		return getAllOrderedByForProblem(predName, "", orderBy, castToInt);
	}

	public List<Tuple> getAllOrderedByForProblem(String predName, String problemId, int[] orderBy) {
		return getAllOrderedByForProblem(problemId, predName, orderBy, false);
	}

	public List<Tuple> getAllOrderedByForProblem(String predName, String problemId, int[] orderBy, boolean castToInt) {
		OrderByStatement orderByStmt = new OrderByStatement();
		OrderByStatement.CastType castType = (castToInt) ? OrderByStatement.CastType.INT : OrderByStatement.CastType.NO_CAST;
		List<String> cols = new PredicateInfo(predicates.get(predName)).argumentColumns();
		for (int col : orderBy)
			orderByStmt.orderBy(cols.get(col), castType);
		return getAllWhereOrderBy(predName, new WhereStatement().ownedByProblem(problemId), orderByStmt);
	}

	public Map<Integer, Set<Tuple>> getPartitionIdToAtoms(String predName) {
		return getPartitionIdToAtomsForProblem(predName, "");
	}

	public Map<Integer, Set<Tuple>> getPartitionIdToAtomsForProblem(String predName, String problemId) {
		Map<Integer, Set<Tuple>> results = new HashMap<>();
		for (RankingEntry<AtomTemplate> re : getAllWhereOrderByWithValueAndPartition(
				predName, new WhereStatement().ownedByProblem(problemId), new OrderByStatement())) {
			results.computeIfAbsent(Integer.parseInt(re.extraInformation), x -> new HashSet<>())
					.add(re.key.getArgTuple());
		}
		return results;
	}

	public Map<Tuple, Double> getAllWithValue(String predName) {
		return getAllWithValueForProblem(predName, "");
	}

	public Map<Tuple, Double> getAllWithValueForProblem(String predName, String problemId) {
		return getAllWhereOrderByWithValueAndPartition(
				predName, new WhereStatement().ownedByProblem(problemId), new OrderByStatement()).stream()
				.collect(Collectors.toMap(rankEntry -> rankEntry.key.getArgTuple(),
						rankEntry -> rankEntry.value));
	}

	// Note:
	// the result can be sorted after the method call, but isn't sorted yet
	public Multimap<String, RankingEntry<AtomTemplate>> getAtomValuesByPredicate(String problemId, Set<String> predicates) {
		Multimap<String, RankingEntry<AtomTemplate>> results = new Multimap<>(CollectionType.LIST);
		for (String predName : predicates) {
			results.putAll(predName,
					getAllWhereOrderByWithValueAndPartition(predName,
							new WhereStatement().ownedByProblem(problemId),
							new OrderByStatement()));
		}
		return results;
	}

	public List<Tuple> getAllMatching(String predName, AtomTemplate match) {
		return getAllMatchingForProblem(predName, "", match);
	}

	public List<Tuple> getAllMatchingForProblem(String predName, String problemId, AtomTemplate match) {
		return getAllWhere(predName, new WhereStatement().ownedByProblem(problemId).matchAtoms(match), match);
	}

	public List<Tuple> getAllAboveThreshold(String predName, double threshold) {
		return getAllAboveThresholdForProblem(predName, "", threshold);
	}

	public List<Tuple> getAllAboveThresholdForProblem(String predName, String problemId, double threshold) {
		return getAllBetweenValuesForProblem(predName, problemId, threshold, WhereStatement.DEFAULT_BELIEF_LESS_THAN);
	}

	public List<Tuple> getAllBelowThreshold(String predName, double threshold) {
		return getAllBelowThresholdForProblem(predName, "", threshold);
	}

	public List<Tuple> getAllBelowThresholdForProblem(String predName, String problemId, double threshold) {
		return getAllBetweenValuesForProblem(predName, problemId, WhereStatement.DEFAULT_BELIEF_GREATER_THAN, threshold);
	}

	public List<Tuple> getAllBetweenValues(String predName, double lower, double upper) {
		return getAllBetweenValuesForProblem(predName, "", lower, upper);
	}

	public List<Tuple> getAllBetweenValuesForProblem(String predName, String problemId, double lower, double upper) {
		return getAllWhere(predName, new WhereStatement()
				.ownedByProblem(problemId).beliefAboveThreshold(lower).beliefBelowThreshold(upper));
	}

	public List<Tuple> getAllWhere(String predName, WhereStatement where, AtomTemplate... atoms) {
		return getAllWhereOrderBy(predName, where, new OrderByStatement(), atoms);
	}

	public List<Tuple> getAllWhereOrderBy(String predName, WhereStatement where, OrderByStatement orderBy,
									AtomTemplate... atoms) {
		List<Tuple> tuples = new ArrayList<>();
		Predicate pred = predicates.get(predName);
		if (pred != null) {
			PredicateInfo predInfo = new PredicateInfo(pred);
			String stmt = "SELECT " + PRED_SHORT + "."
					+ StringUtils.join(predInfo.argumentColumns(), ", " + PRED_SHORT + ".")
					+ " FROM " + predInfo.tableName() + " " + PRED_SHORT + ", "
					+ PROBLEMS2ATOMS_TABLE + " " + P2A_SHORT
					+ where.toString() + " " + orderBy.toString() + ";";

			try (Connection conn = dataStore.getConnection()) {
				ResultSet res = runQueryOn(conn, stmt, atoms);
				while (res.next()) {
					Tuple tuple = new Tuple();
					for (int i = 1; i <= pred.getArity(); i++) {
//						System.err.print(res.getString(i) + " ");
						tuple.addElement(res.getString(i));
					}
//					System.err.println();
					tuples.add(tuple);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return tuples;
	}
	
	public List<RankingEntry<AtomTemplate>> getAtoms(String predName, AtomTemplate... atoms) {
		return getAllWhereOrderByWithValueAndPartition(
				predName, new WhereStatement().matchAtoms(atoms), new OrderByStatement(), atoms);
	}

	public List<RankingEntry<AtomTemplate>> getAllWhereOrderByWithValueAndPartition(
			String predName, WhereStatement where, OrderByStatement orderBy, AtomTemplate... atoms) {
		List<RankingEntry<AtomTemplate>> tuples = new ArrayList<>();
		Predicate pred = predicates.get(predName);
		if (pred != null) {
			PredicateInfo predInfo = new PredicateInfo(pred);
			String stmt = "SELECT " + PRED_SHORT + "." + PredicateInfo.PARTITION_COLUMN_NAME + ", "
					+ PRED_SHORT + "." + PredicateInfo.VALUE_COLUMN_NAME + ", "
					+ PRED_SHORT + "." + StringUtils.join(predInfo.argumentColumns(), ", " + PRED_SHORT + ".")
					+ " FROM " + predInfo.tableName() + " " + PRED_SHORT + ", "
					+ PROBLEMS2ATOMS_TABLE + " " + P2A_SHORT
					+ where.toString() + " " + orderBy.toString() + ";";

			try (Connection conn = dataStore.getConnection()) {
				ResultSet res = runQueryOn(conn, stmt, atoms);
				while (res.next()) {
					String partitionId = res.getString(1);
					double value = res.getDouble(2);
					String[] args = new String[pred.getArity()];
					for (int i = 0; i < pred.getArity(); i++) {
//						System.err.print(res.getString(i + 3) + " ");
						args[i] = res.getString(i + 3);
					}
//					System.err.println();
					tuples.add(new RankingEntry<>(new AtomTemplate(predName, args), partitionId, value));
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return tuples;
	}

	public List<String> getTable(PredicateInfo predicate) {
		List<String> table = new ArrayList<String>();
		try (Connection conn = dataStore.getConnection();
			 PreparedStatement prepStmt = conn.prepareStatement("SELECT * FROM " + predicate.tableName() + ";");) {
			ResultSet rs = prepStmt.executeQuery();
			ResultSetMetaData rsmd = rs.getMetaData();
			while (rs.next()) {
				StringBuilder sb = new StringBuilder();
				for (int i = 1; i <= rsmd.getColumnCount(); i++) {
					sb.append(rs.getString(i));
					sb.append(" ");
				}
				table.add(sb.toString());
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return table;
	}


	////////////
	// ADDERS //
	////////////

	public void declarePredicate(String name, int arity) {
		declarePredicate(new TalkingPredicate(name, arity));
	}

	public void declarePredicate(TalkingPredicate pred) {
		talkingPredicates.put(pred.getSymbol(), pred);
		String name = pred.getSymbol();
		int arity = pred.getArity();
		ConstantType[] args = new ConstantType[arity];
		Arrays.fill(args, ARG_TYPE);
		StandardPredicate stdPred = StandardPredicate.get(name, args);
		try (Connection conn = dataStore.getConnection()) {
			dataStore.registerPredicate(stdPred);

			PredicateInfo predInfo = new PredicateInfo(stdPred);
			String stmt = "ALTER TABLE " + predInfo.tableName()
					+ " ADD " + ATOM_ID_COLUMN_NAME + " INT;";
			stmt += " CREATE TRIGGER atom_del_" + name + " AFTER DELETE ON " + predInfo.tableName()
					+ " FOR EACH ROW CALL \"" + AtomDeletionTrigger.class.getName() + "\";";

			PreparedStatement prepStmt = conn.prepareStatement(stmt);
			prepStmt.execute();
		} catch (Exception e) {
			System.err.println("Ignoring problem when registering predicate: " + e.getClass() + ": " + e.getMessage());
		}
		predicates.put(name, stdPred);
	}

	public void addAtom(String problemId, String predName, String... tuple) {
		addAtom(problemId, predName, DEFAULT_BELIEF, tuple);
	}

	public void addAtom(String problemId, String predName, double value, String... tuple) {
		if (blacklist.contains(predName, tuple))
			return;

		System.err.println("Adding atom " + predName + " " + Arrays.toString(tuple) + " " + value);
		StandardPredicate pred = predicates.get(predName);
		if (pred == null) {
			System.err.println("WARNING: Undeclared predicate \"" + predName + "\"! Ignoring atom.");
		}
		else {
			if (tuple.length != pred.getArity()) {
				System.err.print("WARNING: Wrong number of arguments (you entered " + tuple.length
						+ ", but " + predName + " requires " + pred.getArity() + ")!");
				String[] newArgs = new String[pred.getArity()];
				if (tuple.length < pred.getArity()) {
					System.err.println(" Filling remaining slots with empty string.");
					System.arraycopy(tuple, 0, newArgs, 0, tuple.length);
					Arrays.fill(newArgs, tuple.length, pred.getArity(), "");
				}
				else if (tuple.length > pred.getArity()) {
					System.err.println(" Removing superfluous arguments.");
					System.arraycopy(tuple, 0, newArgs, 0, pred.getArity());
				}
				tuple = newArgs;
			}

			atomsTotal += registerAtom(problemId, predName, stdPartition.getID(), value, tuple);

			if (atomsTotal % 100000 == 0) {
				Runtime runtime = Runtime.getRuntime();
				runtime.gc();
				long usedNow = runtime.totalMemory() - runtime.freeMemory();
				long addedMemory = (usedNow - startMemory) / 1024 / 1024;
				System.err.println("#atoms: " + atomsTotal
						+ ", expansion of memory footprint since starting to add atoms: " + addedMemory + " MB");
			}
			problemsToAtoms.put(problemId, new AtomTemplate(predName, tuple));
		}
	}

	private int registerAtom(String problemId, String predName, int partition, double value, String... args) {
		try (Connection conn = dataStore.getConnection()) {
			int id = associateAtomWithProblem(conn, problemId, predName, args);
			if (id < 0) {
				id = -(id + 1);
				PredicateInfo predInfo = getPredicateInfo(predName);
				String insertAtomStmt = " INSERT INTO " + predInfo.tableName() + "("
						+ ATOM_ID_COLUMN_NAME + ", " + PredicateInfo.PARTITION_COLUMN_NAME + ", "
						+ PredicateInfo.VALUE_COLUMN_NAME + ", " + StringUtils.join(predInfo.argumentColumns(), ", ")
						+ ") VALUES ("
						+ id + ", " + partition + ", " + value + ", '" + StringUtils.join(args, "', '")
						+ "');";
				PreparedStatement insertAtomPrepStmt = conn.prepareStatement(insertAtomStmt);
				return insertAtomPrepStmt.executeUpdate();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return 0;
	}

	private int nextAtomId() {
		return nextId++;
	}
	
	public void associateAtomWithProblem(String problemId, AtomTemplate atom) {
		try (Connection conn = dataStore.getConnection()) {
			associateAtomWithProblem(conn, problemId, atom.getPredicateName(), atom.getArgs());
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private int associateAtomWithProblem(Connection conn, String problemId, String predName, String... args) throws SQLException {
		Predicate pred = predicates.get(predName);
		PredicateInfo predInfo = new PredicateInfo(pred);
		String getIdStmt = "SELECT " + ATOM_ID_COLUMN_NAME + " FROM " + predInfo.tableName() + " " + PRED_SHORT
				+ new WhereStatement().matchAtoms(new AtomTemplate(predName, args)) + ";";
		PreparedStatement getIdPrepStmt = conn.prepareStatement(getIdStmt);
		for (int i = 0; i < args.length; i++)
			getIdPrepStmt.setString(i + 1, args[i]);
		ResultSet res = getIdPrepStmt.executeQuery();
		boolean atomInDB = res.next();
		int id = (atomInDB) ? res.getInt(1) : nextAtomId();

		String insertMapStmt = "MERGE INTO " + PROBLEMS2ATOMS_TABLE + "("
				+ PROBLEM_ID_COLUMN_NAME + ", " + PREDICATE_NAME_COLUMN_NAME + ", "
				+ ATOM_ID_COLUMN_NAME + ") VALUES ("
				+ "?, ?, " + id + ");";
		PreparedStatement insertMapPrepStmt = conn.prepareStatement(insertMapStmt);
		insertMapPrepStmt.setString(1, problemId);
		insertMapPrepStmt.setString(2, predName);
		insertMapPrepStmt.execute();

		return (atomInDB) ? id : -(id + 1);
	}


	/////////////
	// SETTERS //
	/////////////

	public int setAtomsToValue(String predName, AtomTemplate atomMatch, double value) {
		return setAtomsToValueForProblem(predName, "", atomMatch, value);
	}

	public int setAtomsToValueForProblem(String predName, String problemId, AtomTemplate atomMatch, double value) {
		Predicate pred = predicates.get(predName);
		if (pred != null) {
			PredicateInfo predInfo = new PredicateInfo(pred);
			WhereStatement where = new WhereStatement().ownedByProblem(problemId).matchAtoms(atomMatch);
			String stmt = "UPDATE " + predInfo.tableName()
					+ " SET " + PredicateInfo.VALUE_COLUMN_NAME + " = " + value
					+ where;
			return runUpdateOn(stmt, atomMatch);
		}
		return 0;
	}


	///////////////////
	// DELETION CODE //
	///////////////////

	public int deleteUnused(String predName) {
		Predicate pred = predicates.get(predName);
		if (pred != null) {
			PredicateInfo predInfo = new PredicateInfo(pred);
			String stmt = "DELETE FROM " + predInfo.tableName() + " " + PRED_SHORT
					+ " WHERE " + PRED_SHORT + "." + ATOM_ID_COLUMN_NAME + " NOT IN ("
					+ "SELECT " + P2A_SHORT + "." + ATOM_ID_COLUMN_NAME
					+ " FROM " + PROBLEMS2ATOMS_TABLE + " " + P2A_SHORT + ");";
			return runUpdateOn(stmt);
		}
		return 0;
	}

	public boolean deleteAtomPermanently(String predName, AtomTemplate atom) {
		blacklist.add(predName, atom.getArgs());
		return deleteAtomsGlobally(predName, atom) > 0;
	}

	public int deleteAtomsGlobally(String predName, AtomTemplate... atoms) {
		return deleteAtomsGloballyWhere(predName, new WhereStatement().matchAtoms(atoms), atoms);
	}

	public int deleteAtomsForProblem(String predName, String problemId, AtomTemplate... atoms) {
		return deleteAtomsForProblem(predName, problemId, false, atoms);
	}

	public int deleteAtomsForProblem(String predName, String problemId, boolean auxiliary, AtomTemplate... atoms) {
		return deleteAtomsForProblemWhere(
				predName, problemId, new WhereStatement().matchAtoms(atoms), auxiliary, atoms);
	}

	public int deleteAtomsForProblemBelowThreshold(String predName, String problemId, double threshold) {
		return deleteAtomsForProblemBelowThreshold(predName, problemId, false, threshold);
	}

	public int deleteAtomsForProblemBelowThreshold(String predName, String problemId, boolean auxiliary, double threshold) {
		return deleteAtomsForProblemWhere(
				predName, problemId, new WhereStatement().beliefBelowThreshold(threshold), auxiliary);
	}

	private int deleteAtomsGloballyWhere(String predName, WhereStatement where, AtomTemplate... atoms) {
		Predicate pred = predicates.get(predName);
		if (pred != null) {
			PredicateInfo predInfo = new PredicateInfo(pred);
			String stmt = "DELETE FROM " + predInfo.tableName() + " " + PRED_SHORT
					+ where.problemMapTableKnown(false);
			return runUpdateOn(stmt, atoms);
		}
		return 0;
	}

	private int deleteAtomsForProblemWhere(String predName, String problemId, WhereStatement where,
										   boolean auxiliary, AtomTemplate... atoms) {
		Predicate pred = predicates.get(predName);
		if (pred != null) {
			PredicateInfo predInfo = new PredicateInfo(pred);
			String stmt = "DELETE FROM " + PROBLEMS2ATOMS_TABLE + " " + P2A_SHORT
					+ " WHERE " + P2A_SHORT + "." + PROBLEM_ID_COLUMN_NAME + " = '" + problemId
					+ "' AND " + P2A_SHORT + "." + ATOM_ID_COLUMN_NAME + " IN ("
					+ "SELECT " + PRED_SHORT + "." + ATOM_ID_COLUMN_NAME
					+ " FROM " + predInfo.tableName() + " " + PRED_SHORT
					+ where + ");";
			int deleted = 0;
			if (auxiliary) {
				String auxName = PslProblem.existentialAtomName(predName);
				PredicateInfo auxPredInfo = getPredicateInfo(auxName);
				String auxStmt = "DELETE FROM " + PROBLEMS2ATOMS_TABLE + " " + P2A_SHORT
						+ " WHERE " + P2A_SHORT + "." + PROBLEM_ID_COLUMN_NAME + " = '" + problemId
						+ "' AND " + P2A_SHORT + "." + ATOM_ID_COLUMN_NAME + " IN ("
						+ "SELECT " + AUX_SHORT + "." + ATOM_ID_COLUMN_NAME
						+ " FROM " + auxPredInfo.tableName() + " " + AUX_SHORT
						+ ", " + predInfo.tableName() + " " + PRED_SHORT
						+ where.matchAuxiliary(auxName) + ");";
				deleted = runUpdateOn(auxStmt, atoms);
			}
			return deleted + runUpdateOn(stmt, atoms);
		}
		return 0;
	}

	////////////////////////////
	// PARTITION MANIPULATION //
	////////////////////////////

	public int moveToPartition(Set<Integer> sourceIDs, int targetID, AtomTemplate atomMatch) {
		return setPartitionWhere(atomMatch.getPredicateName(), targetID,
				new WhereStatement().isInPartition(sourceIDs).matchAtoms(atomMatch), atomMatch);
	}

	public int moveToPartitionForProblem(Set<Integer> sourceIDs, int targetID, String predName, String problemId) {
		return setPartitionWhere(predName, targetID,
				new WhereStatement().isInPartition(sourceIDs).ownedByProblem(problemId));
	}

	private int setPartitionWhere(String predName, int partitionId, WhereStatement where, AtomTemplate... atoms) {
		Predicate pred = predicates.get(predName);
		if (pred != null) {
			PredicateInfo predInfo = new PredicateInfo(pred);
			String stmt = "UPDATE " + predInfo.tableName() + " " + PRED_SHORT
					+ " SET " + PredicateInfo.PARTITION_COLUMN_NAME + " = " + partitionId
					+ where.problemMapTableKnown(false) + ";";
			return runUpdateOn(stmt, atoms);
		}
		return 0;
	}


	//////////////
	// PRINTERS //
	//////////////

	public void printTable(PredicateInfo predicate) {
		System.out.println(predicate.tableName());
		System.out.println("(partition, value, args)");
		for (String s : getTable(predicate)) {
			System.out.println(s);
		}
	}
	
	public void printTable(String predicate) {
		printTable(new PredicateInfo(predicates.get(predicate)));
	}

	public void print(String problemId, Set<String> predicates, PrintStream printStream) {
		print(problemId, predicates, printStream, false, false);
	}

	public void printWithValue(String problemId, Set<String> predicates, PrintStream printStream) {
		print(problemId, predicates, printStream, true, false);
	}

	public void printWithPartition(String problemId, Set<String> predicates, PrintStream printStream) {
		print(problemId, predicates, printStream, false, true);
	}

	public void printWithValueAndPartition(String problemId, Set<String> predicates, PrintStream printStream) {
		print(problemId, predicates, printStream, true, true);
	}

	private void print(String problemId, Set<String> predicates, PrintStream printStream,
					   boolean printValue, boolean printPartition) {
		Multimap<String, RankingEntry<AtomTemplate>> predicatesToAtoms = getAtomValuesByPredicate(problemId, predicates);
		for (String predicate : predicatesToAtoms.keySet()) {
			List<RankingEntry<AtomTemplate>> atoms = predicatesToAtoms.getList(predicate);
			atoms.sort(null);
			for (RankingEntry<AtomTemplate> rankingEntry : atoms) {
				printStream.print(rankingEntry.key);
				if (printPartition) {
					printStream.print("\t" + rankingEntry.extraInformation);
				}
				if (printValue) {
					printStream.print("\t" + rankingEntry.value);
				}
				printStream.println();
			}
		}
	}

	// **********************************************
	// * Helper methods (mostly for SQL statements) *
	// **********************************************

	private PredicateInfo getPredicateInfo(String predName) {
		return new PredicateInfo(predicates.get(predName));
	}

	private PredicateInfo getPredicateInfo(AtomTemplate atom) {
		return getPredicateInfo(atom.getPredicateName());
	}

	private ResultSet runQueryOn(Connection conn, String query, AtomTemplate... atoms) throws SQLException {
		PreparedStatement prepStmt = conn.prepareStatement(query);
		int p = 1;
		for (AtomTemplate atom : atoms) {
			for (String arg : atom.getArgs())
				if (!arg.equals("?"))
					prepStmt.setString(p++, arg);
		}
		return prepStmt.executeQuery();
	}

	private boolean runQueryResultNotEmpty(String query, AtomTemplate... atoms) {
		try (Connection conn = dataStore.getConnection()) {
			ResultSet res = runQueryOn(conn, query, atoms);
			return res != null && res.next();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	private int runUpdateOn(String query, AtomTemplate... atoms) {
		try (Connection conn = dataStore.getConnection();
			 PreparedStatement prepStmt = conn.prepareStatement(query)) {
			int p = 1;
			for (AtomTemplate atom : atoms) {
				for (String arg : atom.getArgs())
					if (!arg.equals("?"))
						prepStmt.setString(p++, arg);
			}
			return prepStmt.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
			return 0;
		}
	}

	private class WhereStatement {

		private static final double DEFAULT_BELIEF_GREATER_THAN = -1.0;
		private static final double DEFAULT_BELIEF_LESS_THAN = 2.0;

		private boolean p2aTableKnown;

		private String problemId;
		private Set<Integer> inPartitions;
		private double beliefGreaterThan;
		private double beliefLessThan;
		private List<AtomTemplate> atomsToMatch;
		private List<List<String>> productMatch;
		private String auxToMatch;

		public WhereStatement() {
			p2aTableKnown = true;
			problemId = "";
			inPartitions = new HashSet<>();
			beliefGreaterThan = DEFAULT_BELIEF_GREATER_THAN;
			beliefLessThan = DEFAULT_BELIEF_LESS_THAN;
			atomsToMatch = new ArrayList<>();
			auxToMatch = "";
		}

		public WhereStatement problemMapTableKnown(boolean p2aTableKnown) {
			this.p2aTableKnown = p2aTableKnown;
			return this;
		}

		public WhereStatement ownedByProblem(String problemId) {
			return ownedByProblem(problemId, true);
		}

		public WhereStatement ownedByProblem(String problemId, boolean p2aTableKnown) {
			this.problemId = problemId;
			this.p2aTableKnown = p2aTableKnown;
			return this;
		}

		public WhereStatement isInPartition(int... partitionIds) {
			for (int partitionId : partitionIds)
				inPartitions.add(partitionId);
			return this;
		}

		public WhereStatement isInPartition(Collection<Integer> partitionIds) {
			inPartitions.addAll(partitionIds);
			return this;
		}

		public WhereStatement beliefAboveThreshold(double threshold) {
			beliefGreaterThan = threshold;
			return this;
		}

		public WhereStatement beliefBelowThreshold(double threshold) {
			beliefLessThan = threshold;
			return this;
		}

		public WhereStatement beliefBetween(double from, double to) {
			beliefAboveThreshold(from);
			beliefBelowThreshold(to);
			return this;
		}

		public WhereStatement matchAtoms(AtomTemplate... atoms) {
			return matchAtoms(Arrays.asList(atoms));
		}

		public WhereStatement matchAtoms(Collection<AtomTemplate> atoms) {
			atomsToMatch.addAll(atoms);
			return this;
		}

		public WhereStatement matchAuxiliary(String auxiliary) {
			auxToMatch = auxiliary;
			return this;
		}

		public String getConditions() {
			StringBuilder cond = new StringBuilder();

			if (!problemId.isEmpty()) {
				if (p2aTableKnown)
					cond.append("(")
							.append(PRED_SHORT).append(".").append(ATOM_ID_COLUMN_NAME)
							.append(" = ")
							.append(P2A_SHORT).append(".").append(ATOM_ID_COLUMN_NAME)
							.append(" AND ")
							.append(P2A_SHORT).append(".").append(PROBLEM_ID_COLUMN_NAME)
							.append(" = '")
							.append(problemId)
							.append("') AND ");
				else
					cond.append("(")
							.append(PRED_SHORT).append(".").append(ATOM_ID_COLUMN_NAME)
							.append(" IN (")
							.append("SELECT ").append(P2A_SHORT).append(".").append(ATOM_ID_COLUMN_NAME)
							.append(" FROM ").append(PROBLEMS2ATOMS_TABLE).append(" ").append(P2A_SHORT)
							.append(" WHERE ").append(P2A_SHORT).append(".").append(PROBLEM_ID_COLUMN_NAME)
							.append(" = '").append(problemId)
							.append("')) AND ");
			}
			if (!inPartitions.isEmpty()) {
				cond.append("(")
						.append(PRED_SHORT).append(".").append(PredicateInfo.PARTITION_COLUMN_NAME)
						.append(" IN (")
						.append(StringUtils.join(inPartitions, ", "))
						.append(")) AND ");
			}
			if (beliefGreaterThan > DEFAULT_BELIEF_GREATER_THAN) {
				cond.append("(")
						.append(PRED_SHORT).append(".").append(PredicateInfo.VALUE_COLUMN_NAME)
						.append(" > ")
						.append(beliefGreaterThan)
						.append(") AND ");
			}
			if (beliefLessThan < DEFAULT_BELIEF_LESS_THAN) {
				cond.append("(")
						.append(PRED_SHORT).append(".").append(PredicateInfo.VALUE_COLUMN_NAME)
						.append(" < ")
						.append(beliefLessThan)
						.append(") AND ");
			}
			if (!atomsToMatch.isEmpty()) {
				cond.append("("); // *
				boolean whereEmpty = true;
				List<String> cols = getPredicateInfo(atomsToMatch.get(0)).argumentColumns();
				for (AtomTemplate atom : atomsToMatch) {
					String[] args = atom.getArgs();
					boolean atomEmpty = true;
					if (args != null && args.length > 0) {
						cond.append("("); // **
						for (int i = 0; i < args.length; i++) {
							if (!args[i].equals("?")) {
								atomEmpty = false;
								whereEmpty = false;
								cond.append(PRED_SHORT).append(".").append(cols.get(i))
										.append(" = ? AND ");
							}
						}

					}
					if (!atomEmpty) { // delete final AND
						cond.delete(cond.length() - 5, cond.length()).append(") OR ");
					}
					else // delete opening bracket from **
						cond.deleteCharAt(cond.length() - 1);
				}
				if (!whereEmpty) { // delete final OR
					cond.delete(cond.length() - 4, cond.length()).append(") AND ");
				}
				else // delete opening bracket from *
					cond.deleteCharAt(cond.length() - 1);
			}
			if (!auxToMatch.isEmpty()) {
				List<String> cols = getPredicateInfo(auxToMatch).argumentColumns();
				if (!cols.isEmpty()) {
					cond.append("(");
					for (String col : cols) {
						cond.append(AUX_SHORT).append(".").append(col)
								.append(" = ")
								.append(PRED_SHORT).append(".").append(col)
								.append(" AND ");
					}
					cond.delete(cond.length() - 5, cond.length()).append(") AND ");
				}
			}

			if (cond.length() > 0) // delete final AND
				cond.delete(cond.length() - 5, cond.length());

			return cond.toString();
		}

		@Override
		public String toString() {
			String cond = getConditions();
			if (cond.isEmpty())
				return "";

			return " WHERE " + cond;
		}
	}


	private static class OrderByStatement {

		public enum CastType { INT, NO_CAST }

		private List<String> colOrder;
		private List<CastType> castCols;

		public OrderByStatement() {
			colOrder = new ArrayList<>();
			castCols = new ArrayList<>();
		}

		public OrderByStatement orderBy(String col) {
			return orderBy(colOrder.size(), col);
		}

		public OrderByStatement orderBy(String col, CastType cast) {
			return orderBy(colOrder.size(), col, cast);
		}

		public OrderByStatement orderBy(int pos, String col) {
			return orderBy(pos, col, CastType.NO_CAST);
		}

		public OrderByStatement orderBy(int pos, String col, CastType cast) {
			colOrder.add(pos, col);
			castCols.add(pos, cast);
			return this;
		}

		public String getOrder() {
			StringBuilder order = new StringBuilder();

			for (int i = 0; i < colOrder.size(); i++) {
				if (castCols.get(i) == CastType.INT)
					order.append("CAST(").append(colOrder.get(i)).append(" AS INT), ");
				else
					order.append(colOrder.get(i)).append(", ");
			}

			if (order.length() > 0)
				order.delete(order.length() - 2, order.length());

			return order.toString();
		}

		@Override
		public String toString() {
			String order = getOrder();
			if (order.isEmpty())
				return "";
			return " ORDER BY " + order;
		}
	}


	public static class AtomDeletionTrigger implements Trigger {

		@Override
		public void init(Connection connection, String s, String s1, String s2, boolean b, int i) throws SQLException {

		}

		@Override
		public void fire(Connection connection, Object[] deleted, Object[] nothing) throws SQLException {
			try {
				String stmt = "DELETE FROM " + PROBLEMS2ATOMS_TABLE
						+ " WHERE " + ATOM_ID_COLUMN_NAME + " = " + deleted[deleted.length - 1] + ";";
				connection.prepareStatement(stmt).execute();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void close() throws SQLException {

		}

		@Override
		public void remove() throws SQLException {

		}
	}
}

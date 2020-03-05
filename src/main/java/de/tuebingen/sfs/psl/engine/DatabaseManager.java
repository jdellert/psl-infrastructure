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
	private Multimap<String, AtomTemplate> problemsToAtoms;

	private Map<String, Database> problemsToDatabases;
	private int nextId = 0;

	private Partition stdPartition;

	private int atomsTotal;
	private long startMemory;

	private Trie<String> blacklist;

	public DatabaseManager(RDBMSDataStore dataStore) {
		this.dataStore = dataStore;
		predicates = new TreeMap<String, StandardPredicate>();
		talkingPredicates = new TreeMap<String, TalkingPredicate>();
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

	// TODO: How to implement?
	public int getNumberOfTargets(String problemId, Set<AtomTemplate> originalTargets){
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

	public boolean contains(String predName, String... args) {
		StandardPredicate pred = getPredicateByName(predName);
		if (pred != null) {
			PredicateInfo predInfo = new PredicateInfo(pred);
			AtomTemplate atom = new AtomTemplate(predName, args);

			StringBuilder stmt = new StringBuilder();
			stmt.append("SELECT 1 FROM ").append(predInfo.tableName()).append(" ").append(PRED_SHORT);
			WhereStatement where = new WhereStatement();
			stmt.append(where.isInPartition(stdPartition.getID())
					.matchAtoms(atom)
					.toString());

			try (Connection conn = dataStore.getConnection()) {
			ResultSet res = runQueryOn(conn, stmt.toString(), atom);
				return res != null && res.next();
			} catch (SQLException e) {
				e.printStackTrace();
			}
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

	public List<Tuple> getAllMatching(String predName, AtomTemplate match) {
		return getAllMatchingForProblem(predName, "", match);
	}

	public List<Tuple> getAllMatchingForProblem(String predName, String problemId, AtomTemplate match) {
		return getAllWhere(predName, new WhereStatement().ownedByProblem(problemId).matchAtoms(match));
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
						System.err.print(res.getString(i) + " ");
						tuple.addElement(res.getString(i));
					}
					System.err.println();
					tuples.add(tuple);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return tuples;
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

	public void declarePredicate(String name, int arity) {
		declarePredicate(new TalkingPredicate(name, arity));
	}

	public void addAtom(String problemId, String predName, String... tuple) {
		addAtom(problemId, predName, DEFAULT_BELIEF, tuple);
	}

	public void addAtom(String problemId, String predName, double value, String... tuple) {
		if (blacklist.contains(predName, tuple))
			return;

		System.err.println("Adding atom " + predName + " " + Arrays.toString(tuple) + " " + value);
		StandardPredicate pred = predicates.get(predName);
		if (pred == null)
			System.err.println("ERROR: undeclared predicate \"" + predName + "\"!");

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

	private int registerAtom(String problemId, String predName, int partition, double value, String... args) {
		try (Connection conn = dataStore.getConnection()) {
			Predicate pred = predicates.get(predName);
			PredicateInfo predInfo = new PredicateInfo(pred);
			String getIdStmt = attachWhereClause(
					new StringBuilder("SELECT " + ATOM_ID_COLUMN_NAME + " FROM " + predInfo.tableName()),
					new AtomTemplate(predName, args)).toString() + ";";
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

			if (!atomInDB) {
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
	
	public void associateAtomWithProblem(String problemId, AtomTemplate atom){
		problemsToAtoms.put(problemId, atom);
	}


	/////////////
	// SETTERS //
	/////////////

	public int setAtomsToValue(double value, Predicate predicate, int writeID, Set<Integer> readIDs, String... args) {
		PredicateInfo predInfo = new PredicateInfo(predicate);
		StringBuilder setStmt = new StringBuilder();
		setStmt.append("UPDATE ").append(predInfo.tableName()).append(" SET value = ").append(value);
		if (writeID < 0 || readIDs == null){
			attachWhereClause(setStmt, new AtomTemplate(predicate.getName(), args));
		} else {
			attachWhereClauseWithPartitions(setStmt, predInfo, writeID, readIDs, args);
		}

		try (Connection conn = dataStore.getConnection();
			 PreparedStatement prepMoveStmt = conn.prepareStatement(setStmt.toString())) {
			for (int i = 0; i < args.length; i++)
				if (!args[i].equals("?"))
					prepMoveStmt.setString(i + 1, args[i]);
			int count = prepMoveStmt.executeUpdate();
			return count;
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}
	}

	public int setAtomsToValue(double value, Predicate predicate, String... args) {
		return setAtomsToValue(value, predicate, -1, null, args);
	}

	public int setAtomsToValue(double value, String predicate, String... args) {
		return setAtomsToValue(value, predicates.get(predicate), -1, null, args);
	}


	///////////////////
	// DELETION CODE //
	///////////////////

	public void deleteAtoms(String predName, int writeID, Set<Integer> readIDs, String... constantPattern) {
		Predicate pred = getPredicateByName(predName);
		deleteBatch(pred, writeID, readIDs, new AtomTemplate(predName, constantPattern));
	}

	public void deleteAtomPermanently(String predName, int writeID, Set<Integer> readIDs, String... args) {
		deleteAtoms(predName, writeID, readIDs, args);
		blacklist.add(predName, args);
	}

    /**
     * Deletes a batch of atoms satisfying args.
     * Example:
     *    args = [[A, B], [], [C, D]]
     *    => query: DELETE ...
     *              WHERE (col1 = A AND col3 = C)
     *                 OR (col1 = B AND col3 = D) ...
     * All non-empty lists are assumed to be of the same length!
     * @param predicate
     * @param writeID
     * @param readIDs
     * @param args
     * @return
     */
	public int deleteBatch(Predicate predicate, int writeID, Set<Integer> readIDs, AtomTemplate... args) {
		return deleteBatch(new PredicateInfo(predicate), writeID, readIDs, args);
	}

	public int deleteBatch(PredicateInfo predicate, int writeID, Set<Integer> readIDs, AtomTemplate... atoms) {
		StringBuilder stmtBuilder = new StringBuilder();
        stmtBuilder.append("DELETE FROM ").append(predicate.tableName()).append(" WHERE ");
        attachWhereBody(stmtBuilder, atoms).append(whereInPartitions(writeID, readIDs)).append(";");
        String stmt = stmtBuilder.toString();

		try (Connection conn = dataStore.getConnection(); PreparedStatement prepStmt = conn.prepareStatement(stmt);) {
			int c = 1;
			for (AtomTemplate atom : atoms) {
				for (String arg : atom.getArgs()) {
					if (!arg.equals("?")) {
						prepStmt.setString(c, arg);
						c++;
					}
				}
			}
			int count = prepStmt.executeUpdate();
			atomsTotal -= count;
			for (AtomTemplate atom : atoms)
			    deleteFromProblemsToAtomsMap(atom);
			return count;
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}
	}

    /**
     * Deletes a batch of atoms satisfying args.
     * Example:
     *    args = [[A, B], [], [C, D], [E]]
     *    => query: DELETE ...
     *              WHERE col1 IN (A, B)
     *                AND col3 IN (C, D)
     *                AND col4 IN (E) ...
     * Sublists can be of different lengths.
     * @param predicate
     * @param writeID
     * @param readIDs
     * @param args
     * @return
     */
	public int deleteProduct(Predicate predicate, int writeID, Set<Integer> readIDs, List<String>... args) {
		return deleteProduct(new PredicateInfo(predicate), writeID, readIDs, args);
	}

	public int deleteProduct(PredicateInfo predicate, int writeID, Set<Integer> readIDs, List<String>... args) {
		String stmt = prepareDeleteStatementProduct(predicate, writeID, readIDs, args);
		try (Connection conn = dataStore.getConnection(); PreparedStatement prepStmt = conn.prepareStatement(stmt);) {
			int c = 1;
			for (List<String> argList : args) {
				if (argList.size() > 0) {
					for (String arg : argList) {
						prepStmt.setString(c, arg);
						c++;
					}
				}
			}
			int count = prepStmt.executeUpdate();
			atomsTotal -= count;
			for (String[] template : getTemplatesMatchingProduct(0, args))
				deleteFromProblemsToAtomsMap(new AtomTemplate(predicate.predicate().getName(), template));
			return count;
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}
	}

	private List<String[]> getTemplatesMatchingProduct(int i, List<String>... args) {
        List<String[]> templates = new ArrayList<>();
	    if (i == args.length - 1) {
	        for (String finalArg : args[i]) {
                String[] template = new String[args.length];
                template[i] = finalArg;
                templates.add(template);
            }
	        return templates;
        }

        List<String[]> oldTemplates = getTemplatesMatchingProduct(i + 1, args);
        for (String[] template : oldTemplates) {
            if (args[i].isEmpty()) {
                template[i] = "?";
                templates.add(template);
            }
            else {
                for (String arg : args[i]) {
                    String[] newTemplate = template.clone();
                    newTemplate[i] = arg;
                    templates.add(newTemplate);
                }
            }
        }
        return templates;
    }
	
	/*
	 * args = [[A, B], [], [C, D], [E]]
     *    => query: DELETE ...
     *              WHERE col1 IN (A, B)
     *                AND col3 IN (C, D)
     *                AND col4 IN (E) ...
	 */
	public int deleteProduct(String problemId, String predName, List<String>... args) {
		Set<AtomTemplate> toDelete = matchProduct(problemsToAtoms.get(problemId), predName, args);
		return deleteAtomsForPredicate(toDelete.toArray(new AtomTemplate[0]));
	}

    /**
     * Deletes all atoms of a predicate below a certain belief value.
     * @param predicate
     * @param writeID
     * @param readIDs
     * @param threshold
     * @return
     */
	public int deleteBatchBelowThreshold(Predicate predicate, int writeID, Set<Integer> readIDs, double threshold) {
		String stmt = "DELETE FROM " + new PredicateInfo(predicate).tableName() + " WHERE value < " + threshold
				+ " AND " + whereInPartitions(writeID, readIDs) + ";";
		try (Connection conn = dataStore.getConnection(); PreparedStatement prepStmt = conn.prepareStatement(stmt);) {
			int count = prepStmt.executeUpdate();
			atomsTotal -= count;
			return count;
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}
	}
	
    /**
     * Deletes all atoms of a predicate below a certain belief value plus all matching auxiliary ("X") atoms of that
     * predicate.
     * @param predicate
     * @param writeID
     * @param readIDs
     * @param threshold
     * @return
     */
	public int deleteBatchBelowThreshold(Predicate predicate, Predicate auxiliary, int writeID, Set<Integer> readIDs,
			double threshold) {
		PredicateInfo pred = new PredicateInfo(predicate);
		PredicateInfo aux = new PredicateInfo(auxiliary);
		int count;
		String stmt = prepareDeleteStatementForAuxiliary(pred, aux, writeID, readIDs, threshold);
		try (Connection conn = dataStore.getConnection(); PreparedStatement prepStmt = conn.prepareStatement(stmt);) {
			count = prepStmt.executeUpdate();
			atomsTotal -= count;
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}

		return count + deleteBatchBelowThreshold(predicate, writeID, readIDs, threshold);
	}
	
	/**
	 * Deletes all instances of a predicate for a given PSL problem below a given threshold.
	 * @param problemId
	 * @param predicate
	 * @param threshold
	 * @return
	 */
	public int deleteBatchBelowThreshold(String problemId, String predicate, double threshold) {
		List<AtomTemplate> toDelete = new ArrayList<>(); 
		for (AtomTemplate atom : problemsToAtoms.get(problemId)){
			if (atom.getPredicateName().equals(predicate)){
				toDelete.add(atom);
			}
		}

		// TODO: Remove this if problem in deleteAtomsForPredicateIfBelowThreshold is solved
		for (Tuple tuple : getTuplesForPredicateBelowThreshold(problemId, predicate, threshold))
			deleteFromProblemsToAtomsMap(new AtomTemplate(predicate, tuple.toList()));

		return deleteAtomsForPredicateIfBelowThreshold(threshold, toDelete.toArray(new AtomTemplate[0]));		
	}
	
	/**
	 * Deletes all instances of a predicate for a given PSL problem below a given threshold, 
	 * plus all corresponding instances of an auxiliary predicate.
	 * @param problemId
	 * @param predicate
	 * @param auxiliary
	 * @param threshold
	 * @return
	 */
	public int deleteBatchBelowThreshold(String problemId, String predicate, String auxiliary, double threshold) {
		// Delete the auxiliary atoms first
		// (whether they should be deleted depends on the corresponding 'main' atoms' values)
		List<AtomTemplate> toDeleteAux = new ArrayList<>(); for (AtomTemplate atom : problemsToAtoms.get(problemId)){
			if (atom.getPredicateName().equals(auxiliary)){
				toDeleteAux.add(atom);
			}
		}

		// TODO: Remove this if problem below is solved
		for (Tuple tuple : getTuplesForPredicateBelowThreshold(problemId, predicate, threshold))
			deleteFromProblemsToAtomsMap(new AtomTemplate(auxiliary, tuple.toList()));

		String stmt = prepareDeleteStatementForAuxiliary(new PredicateInfo(predicates.get(predicate)), threshold,
				toDeleteAux.toArray(new AtomTemplate[0]));
		int count;
		try (Connection conn = dataStore.getConnection(); PreparedStatement prepStmt = conn.prepareStatement(stmt);) {
			int paramCounter = 1;
			for (AtomTemplate atom : toDeleteAux){
				// The loop needs to be executed twice, once for checking the auxiliary's values...
				for (String arg : atom.getArgs()){
					if (!arg.equals("?")){					
						prepStmt.setString(paramCounter++, arg);
					}
				}
				// ... and once for checking the main atom's values
				for (String arg : atom.getArgs()){
					if (!arg.equals("?")){					
						prepStmt.setString(paramCounter++, arg);
					}
				}
			}
			count = prepStmt.executeUpdate();
			atomsTotal -= count;
			// TODO: This deletes ALL auxiliary atoms, not only those below threshold!!
//			for (AtomTemplate atom : toDeleteAux){
//				deleteFromProblemsToAtomsMap(atom);
//			}
			
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}
		
		// Now delete instances of the main predicate:
		return count + deleteBatchBelowThreshold(problemId, predicate, threshold);
	}
	
	public int deleteAtom(AtomTemplate atom){
		return deleteAtomIfBelowThreshold(atom, -1.0);
	}
	
	public int deleteAtomIfBelowThreshold(AtomTemplate atom, double threshold){
		return deleteAtomsForPredicateIfBelowThreshold(threshold, atom);
	}
	
	
	// Assumes that all atoms belong to the same predicate.
	private int deleteAtomsForPredicate(AtomTemplate... atoms){
		return deleteAtomsForPredicateIfBelowThreshold(-1.0, atoms);
	}
	
	// Assumes that all atoms belong to the same predicate.
	private int deleteAtomsForPredicateIfBelowThreshold(double threshold, AtomTemplate... atoms) {
		String stmt = prepareDeleteStatementBelowThreshold(threshold, atoms);
		try (Connection conn = dataStore.getConnection(); 
				PreparedStatement prepStmt = conn.prepareStatement(stmt);) {
			int paramCounter = 1;
			for (AtomTemplate atom : atoms){				
				for (String arg : atom.getArgs()){
					if (!arg.equals("?")){					
						prepStmt.setString(paramCounter++, arg);
					}
				}
			}
			int count = prepStmt.executeUpdate();
			atomsTotal -= count;

			// TODO: This deletes ALL atoms, not only those below threshold!!
			if (threshold < 0) {
				for (AtomTemplate atom : atoms) {
					deleteFromProblemsToAtomsMap(atom);
				}
			}
			return count;
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}
	}

	private String prepareDeleteStatement(PredicateInfo predicate, int writeID, Set<Integer> readIDs, int nOfOrs,
										  String... args) {
		StringBuilder condBuilder = new StringBuilder("(");
		List<String> cols = predicate.argumentColumns();
		boolean empty = true;
		for (int i = 0; i < args.length; i++) {
			if (!args[i].equals("?")) {
				empty = false;
				condBuilder.append(cols.get(i)).append(" = ? AND ");
			}
		}
		String where = "";
		if (empty)
			where = " WHERE " + whereInPartitions(writeID, readIDs);
		else {
			condBuilder.delete(condBuilder.length() - 5, condBuilder.length()).append(")");
			String cond = condBuilder.toString();
			StringBuilder whereBuilder = new StringBuilder(" WHERE ");
			for (int i = 0; i < nOfOrs; i++)
				whereBuilder.append(cond).append(" OR ");
			whereBuilder.delete(whereBuilder.length() - 4, whereBuilder.length());
			where = whereBuilder.append(" AND ").append(whereInPartitions(writeID, readIDs)).toString();
		}
		return "DELETE FROM " + predicate.tableName() + where + ";";
	}

	private String prepareDeleteStatementProduct(PredicateInfo predicate, int writeID, Set<Integer> readIDs,
												 List<String>... args) {
		StringBuilder stmt = new StringBuilder();
		stmt.append("DELETE FROM ").append(predicate.tableName()).append(" WHERE ");
		List<String> cols = predicate.argumentColumns();
		for (int i = 0; i < args.length; i++) {
			if (args[i].size() > 0) {
				stmt.append(cols.get(i)).append(" IN ").append(getPlaceholders(args[i].size())).append(" AND ");
			}
		}
		stmt.append(whereInPartitions(writeID, readIDs)).append(";");
		return stmt.toString();
	}

	private String prepareDeleteStatementProduct(PredicateInfo predicate, List<String>... args) {
		StringBuilder stmt = new StringBuilder();
		stmt.append("DELETE FROM ").append(predicate.tableName()).append(" WHERE ");
		List<String> cols = predicate.argumentColumns();
		for (int i = 0; i < args.length; i++) {
			if (args[i].size() > 0) {
				stmt.append(cols.get(i)).append(" IN ").append(getPlaceholders(args[i].size())).append(" AND ");
			}
		}
		stmt.append(";");
		return stmt.toString();
	}

	// Assumes that all atoms belong to the same predicate.
	private String prepareDeleteStatement(AtomTemplate... atoms) {
		return prepareDeleteStatementBelowThreshold(-1.0, atoms);
	}

	// Assumes that all atoms belong to the same predicate.
	private String prepareDeleteStatementBelowThreshold(double threshold, AtomTemplate... atoms) {
		PredicateInfo predicate = new PredicateInfo(predicates.get(atoms[0].getPredicateName()));
		StringBuilder stmt = new StringBuilder();
		stmt.append("DELETE FROM ").append(predicate.tableName());
		attachWhereClause(stmt, atoms);
		if (threshold >= 0.0){
			stmt.append("AND value < ").append(threshold);
		}
		stmt.append(";");
		return stmt.toString();
	}

	private String prepareDeleteStatementForAuxiliary(PredicateInfo pred, PredicateInfo aux, int writeID,
													  Set<Integer> readIDs, double threshold) {
		String auxTable = aux.tableName();
		String predTable = pred.tableName();
		StringBuilder stmtBuilder = new StringBuilder();
		stmtBuilder.append("DELETE FROM ").append(auxTable).append(" WHERE ")
				.append(whereInPartitions(writeID, readIDs)).append(" AND EXISTS (SELECT 1 FROM ").append(predTable)
				.append(" WHERE ").append(predTable).append(".value < ").append(threshold).append(" AND ")
				.append(predTable).append(".").append(whereInPartitions(writeID, readIDs));
		for (String col : aux.argumentColumns()) {
			stmtBuilder.append(" AND ").append(predTable).append(".").append(col).append(" = ").append(auxTable)
					.append(".").append(col);
		}
		stmtBuilder.append(");");
		return stmtBuilder.toString();
	}

	// Assumes that all atom templates belong to the same (auxiliary) predicate.
	private String prepareDeleteStatementForAuxiliary(PredicateInfo pred, double threshold, AtomTemplate... auxAtoms) {
		if (auxAtoms.length == 0){
			return ";";
		}

		PredicateInfo aux = new PredicateInfo(predicates.get(auxAtoms[0].getPredicateName()));
		String auxTable = aux.tableName();
		String predTable = pred.tableName();
		StringBuilder stmtBuilder = new StringBuilder();
		List<String> cols = aux.argumentColumns();

		// DELETE FROM auxTable WHERE
		// (arg1 = ? AND arg2 = ?
		//  AND EXISTS (SELECT 1 FROM predTable p WHERE
		//   p.value < threshold
		//   AND p.arg1 = ? AND p.arg2 = ?)
		// ) OR (arg1 = ? AND ......);
		//
		stmtBuilder.append("DELETE FROM ").append(auxTable).append(" WHERE ");
		for (AtomTemplate atom : auxAtoms){
			String[] args = atom.getArgs();
			if (args == null || args.length == 0) {
				continue;
			}

			stmtBuilder.append("(");
			for (int i = 0; i < args.length; i++) {
				if (!args[i].equals("?")) {
					stmtBuilder.append(cols.get(i)).append(" = ? AND ");
				}
			}
			stmtBuilder.append("EXISTS (SELECT 1 FROM ").append(predTable)
					.append(" p WHERE p.value < ").append(threshold);
			for (int i = 0; i < args.length; i++) {
				if (!args[i].equals("?")) {
					stmtBuilder.append(" AND p.");
					stmtBuilder.append(cols.get(i)).append(" = ?");
				}
			}

			stmtBuilder.append(")) OR ");
		}
		stmtBuilder.delete(stmtBuilder.length() - 4, stmtBuilder.length());
		stmtBuilder.append(";");
		return stmtBuilder.toString();
	}

	private void deleteFromProblemsToAtomsMap(AtomTemplate atomToDelete) {
		System.err.println("DELETING " + atomToDelete);

		for (Collection<AtomTemplate> values : problemsToAtoms.values()) {
			Set<AtomTemplate> toBeDeleted = new HashSet<>();
			for (AtomTemplate atom : values){
				// This check works because the atoms in problemsToAtoms cannot contain wildcards.
				if (atom.equalsWithWildcards(atomToDelete)){
					toBeDeleted.add(atom);
				}
			}
			values.removeAll(toBeDeleted);
		}
	}

	////////////////////////////
	// PARTITION MANIPULATION //
	////////////////////////////

	public int moveToPartition(AtomTemplate atom, int sourceID, int targetID) {
		return moveToPartition(sourceID, targetID, new PredicateInfo(predicates.get(atom.getPredicateName())),
				atom.getArgs());
	}
	
	public int moveToPartition(int sourceID, int targetID, String predName, String... args) {
		String sourceStmt = PredicateInfo.PARTITION_COLUMN_NAME + " = " + sourceID;
		return moveToPartition(sourceStmt, targetID, new PredicateInfo(predicates.get(predName)), args);
	}
	
	public int moveToPartition(int sourceID, int targetID, PredicateInfo predicate, String... args) {
		String sourceStmt = PredicateInfo.PARTITION_COLUMN_NAME + " = " + sourceID;
		return moveToPartition(sourceStmt, targetID, predicate, args);
	}
	
	public int moveToPartition(Set<Integer> sourceIDs, int targetID, String predName, String... args) {
		return moveToPartition(sourceIDs, targetID, new PredicateInfo(predicates.get(predName)), args);
	}
	
	public int moveToPartition(Set<Integer> sourceIDs, int targetID, PredicateInfo predicate, String... args) {
		String sourceStmt = PredicateInfo.PARTITION_COLUMN_NAME + " IN (" + StringUtils.join(sourceIDs, ',') + ")";
		return moveToPartition(sourceStmt, targetID, predicate, args);
	}
	
	private int moveToPartition(String sourceStmt, int targetID, PredicateInfo predicate, String... args) {
		StringBuilder moveStmt = new StringBuilder();
		moveStmt.append("UPDATE ").append(predicate.tableName()).append(" SET ")
				.append(PredicateInfo.PARTITION_COLUMN_NAME).append(" = ").append(targetID).append(" WHERE ");
		List<String> cols = predicate.argumentColumns();
		for (int i = 0; i < args.length; i++) {
			if (!args[i].equals("?")) {
				moveStmt.append(cols.get(i)).append(" = ? AND ");
			}
		}
		moveStmt.append(sourceStmt).append(";");

		try (Connection conn = dataStore.getConnection();
				PreparedStatement prepMoveStmt = conn.prepareStatement(moveStmt.toString())) {
			for (int i = 0; i < args.length; i++)
				if (!args[i].equals("?"))
					prepMoveStmt.setString(i + 1, args[i]);
			int count = prepMoveStmt.executeUpdate();
			return count;
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}
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

	private StringBuilder attachWhereClauseWithPartitions(StringBuilder stmt, PredicateInfo predicate, int writeID,
			Set<Integer> readIDs, String... args) {
		stmt.append(" WHERE ");
		List<String> cols = predicate.argumentColumns();
		for (int i = 0; i < args.length; i++) {
			if (!args[i].equals("?")) {
				stmt.append(cols.get(i)).append(" = ? AND ");
			}
		}
		stmt.append(whereInPartitions(writeID, readIDs)).append(";");
		return stmt;
	}

    // Assumes that all atoms belong to the same predicate.
    private StringBuilder attachWhereClause(StringBuilder stmt, AtomTemplate... atoms) {
	    return attachWhereBody(stmt.append(" WHERE "), atoms);
    }

    // Assumes that all atoms belong to the same predicate.
	private StringBuilder attachWhereBody(StringBuilder stmt, AtomTemplate... atoms) {
		if (atoms.length == 0){
			return stmt;
		}
	    StringBuilder where = new StringBuilder("(");
	    boolean whereEmpty = true;
        List<String> cols = getPredicateInfo(atoms[0]).argumentColumns();
	    for (AtomTemplate atom : atoms) {
            String[] args = atom.getArgs();
            boolean atomEmpty = true;
            if (args != null && args.length > 0) {
                where.append("(");
                for (int i = 0; i < args.length; i++) {
                    if (!args[i].equals("?")) {
                        atomEmpty = false;
                        whereEmpty = false;
                        where.append(cols.get(i)).append(" = ? AND ");
                    }
                }

            }
            if (!atomEmpty){            	
            	where.delete(where.length() - 5, where.length()).append(") OR ");
            }
        }
	    if (!whereEmpty){	    	
	    	stmt.append(where.delete(where.length() - 4, where.length())).append(")");
	    }
		return stmt;
	}
	
	private String orderByStatement(PredicateInfo pred, int[] orderBy, boolean castToInt){
		List<String> argCols = pred.argumentColumns();
		String orderByStmt = Arrays.stream(orderBy).mapToObj(argCols::get)
				.collect(Collectors.joining((castToInt) ? " as INT), cast(" : ", "));
		if (castToInt)
			orderByStmt = "cast(" + orderByStmt + " as INT)";
		return " ORDER BY " + orderByStmt;
	}

	private String whereInPartitions(int writeID, Set<Integer> readIDs) {
		return PredicateInfo.PARTITION_COLUMN_NAME + " IN (" + StringUtils.join(readIDs, ',') + "," + writeID + ")";
	}

	private String getPlaceholders(int n) {
		if (n == 1)
			return "(?)";
		StringBuilder placeholders = new StringBuilder("(");
		for (int i = 0; i < n; i++) {
			placeholders.append("?, ");
		}
		placeholders.delete(placeholders.length() - 2, placeholders.length()).append(")");
		return placeholders.toString();
	}

	private PredicateInfo getPredicateInfo(AtomTemplate atom) {
		return new PredicateInfo(predicates.get(atom.getPredicateName()));
	}
	
	private Set<AtomTemplate> matchProduct(Collection<AtomTemplate> candidates, String predName, List<String>... args){
		// This assumes the atom templates don't contain wildcards.
		Set<AtomTemplate> matches = new HashSet<>();
		atomLoop: for (AtomTemplate atom : candidates){
			if (atom.getPredicateName().equals(predName)){
				String[] atomArgs = atom.getArgs();
				if (atomArgs.length < args.length){
					continue;
				}
				argLoop: for (int i = 0; i < args.length; i++){
					if (args[i].isEmpty()){
						continue argLoop;
					}
					boolean match = false;
					for (String argOption : args[i]){
						if (atomArgs[i].equals(argOption)){
							match = true;
							break;
						}
					}
					if (! match){
						continue atomLoop;
					}
				}
				matches.add(atom);
			}
		}
		return matches;
	}


	public ResultSet runQueryOn(Connection conn, String query, AtomTemplate... atoms) throws SQLException {
		PreparedStatement prepStmt = conn.prepareStatement(query);
		int p = 1;
		for (AtomTemplate atom : atoms) {
			for (String arg : atom.getArgs())
				if (!arg.equals("?"))
					prepStmt.setString(p++, arg);
		}
		return prepStmt.executeQuery();
	}

	public int runUpdateOn(String query, AtomTemplate... atoms) {
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

		private String problemId;
		private Set<Integer> inPartitions;
		private double beliefGreaterThan;
		private double beliefLessThan;
		private List<AtomTemplate> atomsToMatch;
		private List<List<String>> productMatch;

		public WhereStatement() {
			problemId = "";
			inPartitions = new HashSet<>();
			beliefGreaterThan = DEFAULT_BELIEF_GREATER_THAN;
			beliefLessThan = DEFAULT_BELIEF_LESS_THAN;
			atomsToMatch = new ArrayList<>();
		}

		public WhereStatement ownedByProblem(String problemId) {
			this.problemId = problemId;
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

		public String getConditions() {
			StringBuilder cond = new StringBuilder();

			if (!problemId.isEmpty()) {
				cond.append("(")
						.append(PRED_SHORT).append(".").append(ATOM_ID_COLUMN_NAME)
						.append(" = ")
						.append(P2A_SHORT).append(".").append(ATOM_ID_COLUMN_NAME)
						.append(" AND ")
						.append(P2A_SHORT).append(".").append(PROBLEM_ID_COLUMN_NAME)
						.append(" = '")
						.append(problemId)
						.append("') AND ");
			}
			if (!inPartitions.isEmpty()) {
				cond.append("(")
						.append(PRED_SHORT).append(".").append(PredicateInfo.PARTITION_COLUMN_NAME)
						.append(" IN (")
						.append(StringUtils.join(inPartitions, ", "))
						.append(") AND ");
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

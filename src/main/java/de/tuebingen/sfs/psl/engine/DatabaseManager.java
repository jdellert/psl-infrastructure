package de.tuebingen.sfs.psl.engine;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.loading.Inserter;
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
	// TODO from ModelStorePSL: replace with UniqueIntID
	private static final ConstantType ARG_TYPE = ConstantType.UniqueStringID;

	private final RDBMSDataStore dataStore;
	Map<String, TalkingPredicate> talkingPredicates;
	Map<String, StandardPredicate> predicates;
	Multimap<String, AtomTemplate> problemsToAtoms;

	Map<String, Database> problemsToDatabases;

	Partition stdPartition;

	int atomsTotal;
	long startMemory;

	Trie<String> blacklist;

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
	}

	public RDBMSDataStore getDataStore() {
		return dataStore;
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

	public Database getDatabase(String problemId){
		return problemsToDatabases.get(problemId);
	}
	
	public int getNumberOfAtoms(String problemId){
		return problemsToAtoms.get(problemId).size();
	}
	
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

	// *******************************
	// * Adding and removing entries *
	// *******************************

	public void declarePredicate(TalkingPredicate pred) {
		addTalkingPredicate(pred.getSymbol(), pred);
		String name = pred.getSymbol();
		int arity = pred.getArity();
		ConstantType[] args = new ConstantType[arity];
		Arrays.fill(args, ARG_TYPE);
		StandardPredicate stdPred = StandardPredicate.get(name, args);
		try {
			dataStore.registerPredicate(stdPred);
		} catch (Exception e) {
			System.err.println("Ignoring problem when registering predicate: " + e.getClass() + ": " + e.getMessage());
		}
		predicates.put(name, stdPred);
	}

	public void declarePredicate(String name, int arity) {
		declarePredicate(new TalkingPredicate(name, arity));
	}

	// TODO what is this for / why is this public (vbl)
	public void addTalkingPredicate(String predName, TalkingPredicate pred) {
		talkingPredicates.put(predName, pred);
	}

	public void addAtom(String problemId, String predName, String... tuple) {
		addAtom(problemId, predName, 1.0, tuple);
	}

	public void addAtom(String problemId, String predName, double value, String... tuple) {
		if (blacklist.contains(predName, tuple))
			return;

		System.err.println("Adding atom " + predName + " " + Arrays.toString(tuple) + " " + value);
//		closeDatabase();
		StandardPredicate pred = predicates.get(predName);
		if (pred == null)
			System.err.println("ERROR: undeclared predicate \"" + predName + "\"!");
		if (contains(pred, tuple))
			return;

		atomsTotal++;

		Inserter inserter = dataStore.getInserter(pred, stdPartition);
		inserter.insertValue(value, (Object[]) tuple);

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
	
	public void associateAtomWithProblem(String problemId, AtomTemplate atom){
		problemsToAtoms.put(problemId, atom);
	}

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
	private int deleteAtomsForPredicateIfBelowThreshold(double threshold, AtomTemplate... atoms){
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
//			for (AtomTemplate atom : atoms){
//				deleteFromProblemsToAtomsMap(atom);
//			}
			return count;
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}
	}

	// *********************
	// * Modifying entries *
	// *********************

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

	// *************************
	// * Querying the database *
	// *************************

	public StandardPredicate getPredicateByName(String predicate) {
		return predicates.get(predicate);
	}

	public Map<String, TalkingPredicate> getTalkingPredicates() {
		return talkingPredicates;
	}

	public boolean contains(String predName, String... args) {
		StandardPredicate pred = getPredicateByName(predName);
		if (pred == null)
			return false;

		return contains(pred, args);
	}

	public boolean contains(StandardPredicate pred, String... args) {
		PredicateInfo predInfo = new PredicateInfo(pred);

		StringBuilder stmt = new StringBuilder();
		stmt.append("SELECT 1 FROM ").append(predInfo.tableName());
		Set<Integer> readIDs = new HashSet<>();
		readIDs.add(stdPartition.getID());
		attachWhereClauseWithPartitions(stmt, predInfo, stdPartition.getID(), readIDs, args);

		try (Connection conn = dataStore.getConnection();
				PreparedStatement prepStmt = conn.prepareStatement(stmt.toString());) {
			for (int i = 0; i < args.length; i++)
				if (!args[i].equals("?"))
					prepStmt.setString(i + 1, args[i]);
			ResultSet res = prepStmt.executeQuery();
			return res.next();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean contains(Predicate predicate, int writeID, Set<Integer> readIDs, String... args) {
		return contains(new PredicateInfo(predicate), writeID, readIDs, args);
	}

	public boolean contains(PredicateInfo predicate, int writeID, Set<Integer> readIDs, String... args) {
		StringBuilder stmt = new StringBuilder();
		stmt.append("SELECT 1 FROM ").append(predicate.tableName());
		attachWhereClauseWithPartitions(stmt, predicate, writeID, readIDs, args);

		try (Connection conn = dataStore.getConnection();
				PreparedStatement prepStmt = conn.prepareStatement(stmt.toString());) {
			for (int i = 0; i < args.length; i++)
				if (!args[i].equals("?"))
					prepStmt.setString(i + 1, args[i]);
			ResultSet res = prepStmt.executeQuery();
			return res.next();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	public List<Tuple> getAll(Predicate predicate, int writeID, Set<Integer> readIDs) {
		return getAllWhere(predicate, writeID, readIDs, "");
	}

	public List<Tuple> getAtomsBetweenValues(Predicate predicate, int writeID, Set<Integer> readIDs, double lower,
			double upper) {
		return getAllWhere(predicate, writeID, readIDs, "value BETWEEN " + lower + " AND " + upper);
	}
	
	public List<Tuple> getAllOrderedBy(String problemId, String predName, int[] orderBy) {
		return getAllOrderedBy(problemId, predName, orderBy, false);
	}
	
	public List<Tuple> getAllOrderedBy(String problemId, String predName, int[] orderBy, boolean castToInt) {
		Predicate pred = predicates.get(predName);
		PredicateInfo predInfo = new PredicateInfo(pred);
		List<AtomTemplate> atoms = new ArrayList<>();
		for (AtomTemplate atom : problemsToAtoms.get(problemId)){
			if (atom.getPredicateName().equals(predName)){
				atoms.add(atom);
			}
		}
        List<String> argCols = predInfo.argumentColumns();
        String cols = StringUtils.join(argCols, "`,`");
        StringBuilder stmt = new StringBuilder();
        stmt.append("SELECT `").append(cols).append("` FROM ").append(predInfo.tableName());
        attachWhereClause(stmt, atoms.toArray(new AtomTemplate[0]));
        stmt.append(orderByStatement(predInfo, orderBy, castToInt)).append(";");
        List<Tuple> tuples = new ArrayList<>();
        try (
                Connection conn = dataStore.getConnection();
                PreparedStatement prepStmt = conn.prepareStatement(stmt.toString());
        ) {
        	int paramCounter = 1;
        	for (AtomTemplate atom : atoms){				
				for (String arg : atom.getArgs()){
					if (!arg.equals("?")){					
						prepStmt.setString(paramCounter++, arg);
					}
				}
			}
            ResultSet res = prepStmt.executeQuery();
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
        return tuples;
    }

	public List<Tuple> getAllOrderedBy(Predicate predicate, int writeID, Set<Integer> readIDs, int[] orderBy) {
		return getAllWhere(predicate, writeID, readIDs, "", orderBy, false);
	}

	public List<Tuple> getAllOrderedBy(Predicate predicate, int writeID, Set<Integer> readIDs, int[] orderBy,
			boolean castToInt) {
		return getAllWhere(predicate, writeID, readIDs, "", orderBy, castToInt);
	}

	private List<Tuple> getAllWhere(Predicate predicate, int writeID, Set<Integer> readIDs, String where) {
		return getAllWhere(predicate, writeID, readIDs, where, "");
	}

	private List<Tuple> getAllWhere(Predicate predicate, int writeID, Set<Integer> readIDs, String where, int[] orderBy,
			boolean castToInt) {
		List<String> argCols = new PredicateInfo(predicate).argumentColumns();
		String orderByStmt = Arrays.stream(orderBy).mapToObj(argCols::get)
				.collect(Collectors.joining((castToInt) ? " as INT), cast(" : ", "));
		if (castToInt)
			orderByStmt = "cast(" + orderByStmt + " as INT)";
		return getAllWhere(predicate, writeID, readIDs, where, orderByStmt);
	}
	
	private List<Tuple> getAllWhere(Predicate predicate, int writeID, Set<Integer> readIDs, String where, String orderBy) {
        PredicateInfo predInfo = new PredicateInfo(predicate);
        List<String> argCols = predInfo.argumentColumns();
        String cols = StringUtils.join(argCols, "`,`");
        if (!where.isEmpty())
            where += " AND";
        String stmt = "SELECT `" + cols + "` FROM " + predInfo.tableName()
                + " WHERE " + where;
        if (writeID >= 0 && readIDs != null)
        	stmt += " " + whereInPartitions(writeID, readIDs);
        if (orderBy != null && !orderBy.isEmpty())
            stmt += " ORDER BY " + orderBy;
        stmt += ";";
        List<Tuple> tuples = new ArrayList<>();
        try (
                Connection conn = dataStore.getConnection();
                PreparedStatement prepStmt = conn.prepareStatement(stmt);
        ) {
            ResultSet res = prepStmt.executeQuery();
            while (res.next()) {
                Tuple tuple = new Tuple();
                for (int i = 1; i <= predicate.getArity(); i++) {
                    System.err.print(res.getString(i) + " ");
                    tuple.addElement(res.getString(i));
                }
                System.err.println();
                tuples.add(tuple);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tuples;
    }

	public Map<Tuple, Double> getAllWithValue(Predicate predicate, int writeID, Set<Integer> readIDs) {
		PredicateInfo predInfo = new PredicateInfo(predicate);
		String cols = StringUtils.join(predInfo.argumentColumns(), "`,`");
		String stmt = "SELECT `value`,`" + cols + "` FROM " + predInfo.tableName() + " WHERE "
				+ whereInPartitions(writeID, readIDs) + ";";
		Map<Tuple, Double> atomsWithValues = new TreeMap<>();
		try (Connection conn = dataStore.getConnection(); PreparedStatement prepStmt = conn.prepareStatement(stmt);) {
			ResultSet res = prepStmt.executeQuery();
			while (res.next()) {
				double value = res.getDouble(1);
				Tuple tuple = new Tuple();
				for (int i = 2; i <= predicate.getArity() + 1; i++) {
					tuple.addElement(res.getString(i));
				}
				atomsWithValues.put(tuple, value);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return atomsWithValues;
	}

	public Map<Integer, Set<Tuple>> getAllWithPartition(Predicate predicate) {
		PredicateInfo predInfo = new PredicateInfo(predicate);
		String cols = StringUtils.join(predInfo.argumentColumns(), "`,`");
		String stmt = "SELECT `" + PredicateInfo.PARTITION_COLUMN_NAME + "`,`" + cols + "` FROM " + predInfo.tableName()
				+ ";";
		Map<Integer, Set<Tuple>> part2Tuples = new TreeMap<>();
		try (Connection conn = dataStore.getConnection(); PreparedStatement prepStmt = conn.prepareStatement(stmt);) {
			ResultSet res = prepStmt.executeQuery();
			while (res.next()) {
				int partitionID = res.getInt(1);
				Tuple tuple = new Tuple();
				for (int i = 2; i <= predicate.getArity() + 1; i++) {
					tuple.addElement(res.getString(i));
				}
				Set<Tuple> tuples = part2Tuples.get(partitionID);
				if (tuples == null) {
					tuples = new TreeSet<>();
					part2Tuples.put(partitionID, tuples);
				}
				tuples.add(tuple);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return part2Tuples;
	}

	public Set<Tuple> getTuplesForPredicateAndAllProblems(String predName) {
		PredicateInfo pred = new PredicateInfo(predicates.get(predName));
		Set<Tuple> argTuples = new HashSet<Tuple>();

		try (Connection conn = dataStore.getConnection();
				PreparedStatement prepStmt = conn.prepareStatement("SELECT * FROM " + pred.tableName() + ";");) {
			ResultSet rs = prepStmt.executeQuery();
			ResultSetMetaData rsmd = rs.getMetaData();
			while (rs.next()) {
				if (rsmd.getColumnCount() < 3) {
					System.err.println("Could not read an entry in the " + predName + " table: "
							+ "Expected at least 3 columns, found only " + rsmd.getColumnCount());
					continue;
				}
				Tuple args = new Tuple();
				for (int i = 3; i <= rsmd.getColumnCount(); i++) {
					args.addElement(rs.getString(i));
				}
				argTuples.add(args);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return argTuples;
	}

	public Set<Tuple> getTuplesForPredicate(String problemId, String predName) {
		Set<Tuple> tuples = new HashSet<>();
		for (AtomTemplate atom : problemsToAtoms.get(problemId)) {
			if (atom.getPredicateName().equals(predName)) {
				tuples.add(atom.getArgTuple());
			}
		}
		return tuples;
	}
	
	public Set<Tuple> getTuplesForPredicate(String problemId, String predName, String... args) {
		return getTuplesForPredicate(problemId, new AtomTemplate(predName, args));
	}
	
	public Set<Tuple> getTuplesForPredicate(String problemId, AtomTemplate atom) {
		Set<Tuple> tuples = new HashSet<>();
		for (AtomTemplate candidate : problemsToAtoms.get(problemId)) {
			if (candidate.equalsWithWildcards(atom)) {
				tuples.add(candidate.getArgTuple());
			}
		}
		return tuples;
	}
	
	public Set<Tuple> getTuplesForPredicateAboveThreshold(String problemId, String predName, double threshold) {
		return getTuplesForPredicateBetweenValues(problemId, predName, threshold, 1.0);
	}
	
	public Set<Tuple> getTuplesForPredicateBelowThreshold(String problemId, String predName, double threshold) {
		return getTuplesForPredicateBetweenValues(problemId, predName, 0.0, threshold);
	}
	
	public Set<Tuple> getTuplesForPredicateBetweenValues(String problemId, String predName, double lower, double upper) {
		Set<Tuple> tuples = new HashSet<>();
		Set<AtomTemplate> candidates = new HashSet<>();
		for (AtomTemplate atom : problemsToAtoms.get(problemId)){
			if (atom.getPredicateName().equals(predName)){
				candidates.add(atom);
			}
		}
		AtomTemplate[] atoms = candidates.toArray(new AtomTemplate[0]);

		Predicate predicate = predicates.get(predName);
		PredicateInfo predInfo = new PredicateInfo(predicate);
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT `").append(StringUtils.join(predInfo.argumentColumns(), "`,`"));
		sb.append("` FROM ").append(predInfo.tableName());
		attachWhereClause(sb, atoms);
		sb.append(" AND value BETWEEN ").append(lower).append(" AND ").append(upper);
		sb.append(";");
		try (Connection conn = dataStore.getConnection();
				PreparedStatement prepStmt = conn.prepareStatement(sb.toString());) {
			int paramCounter = 1;
			for (AtomTemplate atom : atoms) {
	            String[] args = atom.getArgs();
	            if (args != null && args.length > 0) {
	                for (int i = 0; i < args.length; i++) {
        				if (!args[i].equals("?")){
        					prepStmt.setString(paramCounter++, args[i]);
        				}
	                }
	            }
	        }
			ResultSet rs = prepStmt.executeQuery();
			 while (rs.next()) {
	                Tuple tuple = new Tuple();
	                for (int i = 1; i <= predicate.getArity(); i++) {
	                    System.err.print(rs.getString(i) + " ");
	                    tuple.addElement(rs.getString(i));
	                }
	                System.err.println();
	                tuples.add(tuple);
	            }
		} catch (SQLException e) {
			e.printStackTrace();
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

	// the RankingEntry contains the partition info
	// this can contain multiple results in case the atom arguments contain
	// wildcards
	public List<RankingEntry<AtomTemplate>> getAtomValues(AtomTemplate atom) {
		String predName = atom.getPredicateName();
		PredicateInfo pred = new PredicateInfo(predicates.get(predName));
		List<RankingEntry<AtomTemplate>> atomsWithValues = new ArrayList<>();

		StringBuilder stmt = new StringBuilder();
		stmt.append("SELECT * FROM ");
		stmt.append(pred.tableName());
		attachWhereClause(stmt, atom).append(";");
		System.err.println("Getting values for " + atom);
		try (Connection conn = dataStore.getConnection();
				PreparedStatement prepStmt = conn.prepareStatement(stmt.toString());) {
			String[] templateArgs = atom.getArgs();
			for (int i = 0; i < templateArgs.length; i++){
				if (!templateArgs[i].equals("?")){
					prepStmt.setString(i + 1, templateArgs[i]);
				}
			}
			ResultSet rs = prepStmt.executeQuery();
			ResultSetMetaData rsmd = rs.getMetaData();
			while (rs.next()) {
				if (rsmd.getColumnCount() < 3) {
					System.err.println("Could not read an entry in the " + predName + " table: "
							+ "Expected at least 3 columns, found only " + rsmd.getColumnCount());
					continue;
				}
				// Columns (1-indexed):
				// 1 - partition
				// 2 - value
				// 3+ - args
				String partition = rs.getString(1);
				double value = rs.getDouble(2);
				List<String> args = new ArrayList<>();
				for (int i = 3; i <= rsmd.getColumnCount(); i++) {
					args.add(rs.getString(i));
				}
				// Creating a new AtomTemplate in case the one for querying
				// contains wildcards
				atomsWithValues.add(new RankingEntry<AtomTemplate>(new AtomTemplate(predName, args), partition, value));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return atomsWithValues;
	}

	// Note:
	// the result can be sorted after the method call, but isn't sorted yet
	public Multimap<String, RankingEntry<AtomTemplate>> getAtomValuesByPredicate(Set<AtomTemplate> atomSet) {
		// TODO this could be nicer/more efficient in terms of bundling SQL queries (vbl)
		Multimap<String, RankingEntry<AtomTemplate>> results = new Multimap<>(CollectionType.LIST);
		for (AtomTemplate atom : atomSet) {
			results.putAll(atom.getPredicateName(), getAtomValues(atom));
		}
		return results;
	}

	// ***********************
	// * Print query results *
	// ***********************

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

	public void print(Set<AtomTemplate> atomSet, PrintStream printStream) {
		print(atomSet, printStream, false, false);
	}

	public void printWithValue(Set<AtomTemplate> atomSet, PrintStream printStream) {
		print(atomSet, printStream, true, false);
	}

	public void printWithPartition(Set<AtomTemplate> atomSet, PrintStream printStream) {
		print(atomSet, printStream, false, true);
	}

	public void printWithValueAndPartition(Set<AtomTemplate> atomSet, PrintStream printStream) {
		print(atomSet, printStream, true, true);
	}

	private void print(Set<AtomTemplate> atomSet, PrintStream printStream, boolean printValue, boolean printPartition) {
		Multimap<String, RankingEntry<AtomTemplate>> predicatesToAtoms = getAtomValuesByPredicate(atomSet);
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

}

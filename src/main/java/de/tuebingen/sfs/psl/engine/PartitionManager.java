package de.tuebingen.sfs.psl.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.PredicateInfo;

import de.tuebingen.sfs.psl.util.data.Multimap;
import de.tuebingen.sfs.psl.util.data.Multimap.CollectionType;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;

public class PartitionManager {
	
	// ONLY set this to true if you're working with a VERY small set of atoms.
	public boolean verbose = false;

	public static final String STD_PARTITION_ID = "stdPartition";
	private Partition stdPartition;
	private int nextPartition = 0;

	private DatabaseManager dbManager;
	private Multimap<AtomTemplate, String> atomsToProblemIds; // TODO is this actually used anywhere? (vbl)
	private Multimap<PslProblem, Partition> problemsToReadPartitions;
	private Map<PslProblem, Partition> problemsToWritePartitions;
	private Multimap<Partition, PslProblem> partitionsToProblems;

	public PartitionManager(DatabaseManager dbManager) {
		this.dbManager = dbManager;
		stdPartition = dbManager.getDataStore().getPartition(STD_PARTITION_ID);
		atomsToProblemIds = new Multimap<>(CollectionType.LIST);
		problemsToReadPartitions = new Multimap<>(CollectionType.SET);
		problemsToWritePartitions = new HashMap<>();
		partitionsToProblems = new Multimap<>(CollectionType.SET);
	}

	public DatabaseManager getDbManager() {
		return dbManager;
	}

	protected void registerProblem(PslProblem problem) {
		problemsToReadPartitions.put(problem, stdPartition);
		problemsToWritePartitions.put(problem, stdPartition);
		partitionsToProblems.put(stdPartition, problem);
	}

	private Partition nextFreePartition() {
		return dbManager.getDataStore().getPartition("partition-" + nextPartition++);
	}

	public ProblemWithPartitions preparePartitions(PslProblem problem) {
		List<PslProblem> probs = new ArrayList<>();
		probs.add(problem);
		try {
			return preparePartitions(probs).get(0);
		} catch (PartitionException e) {
			// This exception is only thrown when multiple problems are to be run,
			// i.e. not when this method is called.
			e.printStackTrace();
		}
		return null;
	}
	
	public List<ProblemWithPartitions> preparePartitions(List<PslProblem> problems) throws PartitionException{
		if (problems == null || problems.isEmpty()) {
			System.err.println("No tasks given");
			return null;
		}

		List<ProblemWithPartitions> problemsWithPartitions = new ArrayList<>();

		System.err.println("Checking for overlapping write atoms");
		Multimap<List<PslProblem>, AtomTemplate> problemsToAtoms = getAtomsPerProblem(problems, true);
		boolean overlap = false;
		for (List<PslProblem> overlappingProbs : problemsToAtoms.keySet()) {
			if (overlappingProbs.size() > 1) {
				overlap = true;
				System.out.println("These partions have overlapping write atoms:" + overlappingProbs.stream()
						.map(x -> x.getName().isEmpty() ? x.getClass().getSimpleName() : x.getName())
						.collect(Collectors.toList()));
			}
		}
		if (overlap) {
			// TODO queue (vbl)
			throw new PartitionException("Overlapping partitions");
		}

		// RDBMSManager.printTable(dataStore, new
		// PredicateInfo(problems.get(0).getAtoms().getPredicate("OpenPredA")));
		// RDBMSManager.printTable(dataStore, new
		// PredicateInfo(problems.get(1).getAtoms().getPredicate("OpenPredB")));

		for (PslProblem problem : problems) {
			changeWritePartition(problem, nextFreePartition());
		}

		System.err.println("Checking for overlapping read atoms");
		problemsToAtoms = getAtomsPerProblem(problems, false);
		Set<Partition> originalSources = new HashSet<>();
		for (PslProblem problem : problems) {
			originalSources.addAll(problemsToReadPartitions.get(problem));
		}
		for (Entry<List<PslProblem>, Collection<AtomTemplate>> entry : problemsToAtoms.entrySet()) {
			changeReadPartition(entry.getKey(), entry.getValue(), nextFreePartition());
		}
		
		// Update the partition overview.
		for (PslProblem problem : problems) {
			for (Partition originalSrc : originalSources) {
				// This can cause an error message if the original source is 1, 
				// because it was already removed when updating the write partition. 
				partitionsToProblems.removeFromOrDeleteCollection(originalSrc, problem);
				problemsToReadPartitions.removeFromOrDeleteCollection(problem, originalSrc);
			}
			problemsWithPartitions.add(new ProblemWithPartitions(problem));
		}

		printPartitionSummary();
		return problemsWithPartitions;
	}

	private Multimap<List<PslProblem>, AtomTemplate> getAtomsPerProblem(List<PslProblem> problems, boolean writeAtoms) {
		Multimap<List<PslProblem>, AtomTemplate> problemsToAtoms = new Multimap<>(CollectionType.SET);
		if (problems.size() == 1){
			if (writeAtoms){
				problemsToAtoms.putAll(problems, problems.get(0).reserveAtomsForWriting());
			} else {
				problemsToAtoms.putAll(problems, problems.get(0).declareAtomsForReading());
			}
			return problemsToAtoms;
		}
		
		Multimap<AtomTemplate, PslProblem> atomsToProblems = new Multimap<>(CollectionType.LIST);
		for (PslProblem problem : problems) {
			Set<AtomTemplate> atoms;
			if (writeAtoms) {
				atoms = problem.reserveAtomsForWriting();
			} else {
				atoms = problem.declareAtomsForReading();
			}
			for (AtomTemplate atom : atoms) {
				atomsToProblems.put(atom, problem);
			}
		}

		for (Entry<AtomTemplate, Collection<PslProblem>> entry : atomsToProblems.entrySet()) {
			problemsToAtoms.put((List<PslProblem>) entry.getValue(), entry.getKey());
		}

		return problemsToAtoms;
	}

	private void changeWritePartition(PslProblem problem, Partition targetPartition) {
		System.err.println("Reserving write atoms for " + problem.getName());
		Collection<AtomTemplate> writeAtoms = problem.reserveAtomsForWriting();
		Partition sourcePartition = problemsToWritePartitions.get(problem);
		System.err.println("Moving target atoms for " + problem.getName() + " from " + sourcePartition
				+ " (id: " + sourcePartition.getID() + ") to " + targetPartition + " (id: " + targetPartition.getID() + ")");
		for (AtomTemplate writeAtom : writeAtoms) {
			int rowsMoved = moveToPartition(problem, sourcePartition.getID(), targetPartition.getID(), writeAtom);
			if (verbose){
				System.err
				.println("Tried to move " + writeAtom + " from " + sourcePartition + " to " + targetPartition + ": " + rowsMoved);
			}
			atomsToProblemIds.put(writeAtom, problem.getName());
		}
		partitionsToProblems.removeFromOrDeleteCollection(problemsToWritePartitions.get(problem), problem);
		problemsToWritePartitions.put(problem, targetPartition);
		partitionsToProblems.put(targetPartition, problem);
	}

	private void changeReadPartition(List<PslProblem> problems, Collection<AtomTemplate> readAtoms,
									 Partition targetPartition) {
		System.err.println("Reserving read atoms for "
				+ problems.stream().map(x -> x.getName()).collect(Collectors.toList()));
		if (readAtoms.isEmpty()) {
			System.err.println("Empty atom set");
			return;
		}
		if (verbose){
			System.err.println(readAtoms);
		}

		Set<Partition> potentialSources = new HashSet<>();
		for (PslProblem problem : problems) {
			potentialSources.addAll(problemsToReadPartitions.get(problem));
		}

		// TODO more efficient query (vbl)
		for (AtomTemplate readAtom : readAtoms) {
			for (Partition sourcePartition : potentialSources) {
				int rowsMoved = moveToPartition(problems.get(0), sourcePartition.getID(),
						targetPartition.getID(), readAtom);
				if (verbose){
					System.err.println(
							"Tried to move " + readAtom + " from " + sourcePartition + " to " + targetPartition + ": " + rowsMoved);
				}
				if (rowsMoved > 0) {
					// The atom templates here don't contain wildcards -- each template can only move up to one row.
					break;
				}
			}
		}

		for (PslProblem problem : problems) {
			problemsToReadPartitions.get(problem).add(targetPartition);
			partitionsToProblems.put(targetPartition, problem);
		}
	}

	public void cleanUp(PslProblem problem) {
		// After the inference, move the atoms back to the standard partitions,
		// when possible.
		// TODO for the write partition (which isn't shared!), this could be more efficient 
		// atom args) (vbl)
		changeWritePartition(problem, stdPartition);
		Partition origWrite = problemsToWritePartitions.get(problem);
		partitionsToProblems.removeFromOrDeleteCollection(origWrite, problem);
		problemsToReadPartitions.removeFromOrDeleteCollection(problem, origWrite);

		List<PslProblem> problems = new ArrayList<>();
		problems.add(problem);
		Set<Partition> originalSources = new HashSet<>(problemsToReadPartitions.get(problem));
		changeReadPartition(problems, problem.declareAtomsForReading(), stdPartition);
		for (Partition originalSrc : originalSources) {
			partitionsToProblems.removeFromOrDeleteCollection(originalSrc, problem);
			problemsToReadPartitions.removeFromOrDeleteCollection(problem, originalSrc);
		}
	}

	private int moveToPartition(PslProblem problem, int sourceID, int targetID, AtomTemplate atomTemplate) {
//		System.err.println("Trying to move " + atomTemplate + " from " + sourceID + " to " + targetID);
		PredicateInfo predInfo = new PredicateInfo(dbManager.getPredicateByName(atomTemplate.getPredicateName()));
		int rowsMoved = dbManager.moveToPartition(sourceID, targetID, predInfo, atomTemplate.getArgs());
		// TODO (vbl)
		// ONLY uncomment this if the relevant predicate contains very few atoms
		// else you will spend a lot of time printing entries and pollute the output considerably
		// dbManager.printTable(predInfo);
		return rowsMoved;
	}

	public Partition getWritePartition(PslProblem problem){
		return problemsToWritePartitions.get(problem);
	}

	public int getWritePartitionID(PslProblem problem){
		return getWritePartition(problem).getID();
	}

	public Set<Partition> getReadPartitions(PslProblem problem){
		return problemsToReadPartitions.getSet(problem);
	}
	public Set<Integer> getReadPartitionIDs(PslProblem problem){
		return getReadPartitions(problem).stream().map(Partition::getID).collect(Collectors.toSet());
	}
	
	// For testing:

	public void printReadPartitionsPerProblem() {
		for (String s : getReadPartitionsPerProblem()) {
			System.out.println(s);
		}
	}

	public List<String> getReadPartitionsPerProblem() {
		List<String> readPartitionsPerProblem = new ArrayList<>();
		for (Entry<PslProblem, Collection<Partition>> entry : problemsToReadPartitions.entrySet()) {
			String name = entry.getKey().getName();
			if (name.isEmpty()) {
				name = entry.getKey().getClass().getSimpleName();
			}
			readPartitionsPerProblem.add(name + ": " + Arrays.toString(entry.getValue().toArray()));
		}
		return readPartitionsPerProblem;
	}

	public void printWritePartitionsPerProblem() {
		for (String s : getWritePartitionsPerProblem()) {
			System.out.println(s);
		}
	}

	public List<String> getWritePartitionsPerProblem() {
		List<String> writePartitionsPerProblem = new ArrayList<>();
		for (Entry<PslProblem, Partition> entry : problemsToWritePartitions.entrySet()) {
			String name = entry.getKey().getName();
			if (name.isEmpty()) {
				name = entry.getKey().getClass().getSimpleName();
			}
			writePartitionsPerProblem.add(name + ": " + entry.getValue());
		}
		return writePartitionsPerProblem;
	}

	public void printProblemsPerPartition() {
		for (String s : getProblemsPerPartition()) {
			System.out.println(s);
		}
	}

	public List<String> getProblemsPerPartition() {
		List<String> problemsPerPartition = new ArrayList<>();
		for (Entry<Partition, Collection<PslProblem>> entry : partitionsToProblems.entrySet()) {
			problemsPerPartition.add(entry.getKey().getName() + ": "
					+ entry.getValue().stream()
							.map(x -> x.getName().isEmpty() ? x.getClass().getSimpleName() : x.getName())
							.collect(Collectors.toList()));
		}
		return problemsPerPartition;
	}

	public void printPartitionSummary() {
		System.out.println("All partitions:");
		printProblemsPerPartition();
		System.out.println("------------");
		System.out.println("Read partitions:");
		printReadPartitionsPerProblem();
		System.out.println("------------");
		System.out.println("Write partitions:");
		printWritePartitionsPerProblem();
		System.out.println();
	}

	public class ProblemWithPartitions {

		PslProblem problem;
		Partition writePartition;
		Partition[] readPartitions;

		public ProblemWithPartitions(PslProblem problem) {
			this.problem = problem;
			this.writePartition = problemsToWritePartitions.get(problem);
			this.readPartitions = problemsToReadPartitions.get(problem).toArray(new Partition[0]);
		}

	}

	public class PartitionException extends Exception {

		private static final long serialVersionUID = -1077110774830553521L;

		public PartitionException(String message) {
			super(message);
		}

	}

}

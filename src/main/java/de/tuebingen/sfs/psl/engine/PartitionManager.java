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

import org.linqs.psl.database.rdbms.PredicateInfo;

import de.tuebingen.sfs.psl.util.data.Multimap;
import de.tuebingen.sfs.psl.util.data.Multimap.CollectionType;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;

public class PartitionManager {
	
	// ONLY set this to true if you're working with a VERY small set of atoms.
	public boolean verbose = false;

	// TODO more informative names for partitions (vbl)
	public static final int STD_PARTITION = 1;
	private DatabaseManager dbManager;
	private Multimap<AtomTemplate, String> atomsToProblemIds; // TODO is this actually used anywhere? (vbl)
	private Multimap<PslProblem, Integer> problemsToReadPartitions;
	private Map<PslProblem, Integer> problemsToWritePartitions;
	private Multimap<Integer, PslProblem> partitionsToProblems;

	public PartitionManager(DatabaseManager dbManager) {
		this.dbManager = dbManager;
		atomsToProblemIds = new Multimap<>(CollectionType.LIST);
		problemsToReadPartitions = new Multimap<>(CollectionType.SET);
		problemsToWritePartitions = new HashMap<>();
		partitionsToProblems = new Multimap<>(CollectionType.SET);
	}

	public DatabaseManager getDbManager() {
		return dbManager;
	}

	protected void registerProblem(PslProblem problem) {
		problemsToReadPartitions.put(problem, STD_PARTITION);
		problemsToWritePartitions.put(problem, STD_PARTITION);
		partitionsToProblems.put(STD_PARTITION, problem);
	}

	private Integer nextFreePartition() {
		int i = 1;
		Set<Integer> existingPartitions = partitionsToProblems.keySet();
		while (i++ > 0) {
			if (!existingPartitions.contains(i)) {
				return i;
			}
		}
		return null;
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
		Set<Integer> originalSources = new HashSet<>();
		for (PslProblem problem : problems) {
			originalSources.addAll(problemsToReadPartitions.get(problem));
		}
		for (Entry<List<PslProblem>, Collection<AtomTemplate>> entry : problemsToAtoms.entrySet()) {
			changeReadPartition(entry.getKey(), entry.getValue(), nextFreePartition());
		}
		
		// Update the partition overview.
		for (PslProblem problem : problems) {
			for (Integer originalSrc : originalSources) {
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

	private void changeWritePartition(PslProblem problem, int targetID) {
		System.err.println("Reserving write atoms for " + problem.getName());
		Collection<AtomTemplate> writeAtoms = problem.reserveAtomsForWriting();
		int sourceID = problemsToWritePartitions.get(problem);
		System.err.println("Moving target atoms for " + problem.getName() + " from " + sourceID + " to " + targetID);
		for (AtomTemplate writeAtom : writeAtoms) {
			int rowsMoved = moveToPartition(problem, sourceID, targetID, writeAtom);
			if (verbose){
				System.err
				.println("Tried to move " + writeAtom + " from " + sourceID + " to " + targetID + ": " + rowsMoved);				
			}
			atomsToProblemIds.put(writeAtom, problem.getName());
		}
		partitionsToProblems.removeFromOrDeleteCollection(problemsToWritePartitions.get(problem), problem);
		problemsToWritePartitions.put(problem, targetID);
		partitionsToProblems.put(targetID, problem);
	}

	private void changeReadPartition(List<PslProblem> problems, Collection<AtomTemplate> readAtoms, int targetID) {
		System.err.println("Reserving read atoms for "
				+ problems.stream().map(x -> x.getName()).collect(Collectors.toList()));
		if (readAtoms.isEmpty()) {
			System.err.println("Empty atom set");
			return;
		}
		if (verbose){
			System.err.println(readAtoms);
		}

		Set<Integer> potentialSources = new HashSet<>();
		for (PslProblem problem : problems) {
			potentialSources.addAll(problemsToReadPartitions.get(problem));
		}

		// TODO more efficient query (vbl)
		for (AtomTemplate readAtom : readAtoms) {
			for (Integer sourceID : potentialSources) {
				int rowsMoved = moveToPartition(problems.get(0), sourceID, targetID, readAtom);
				if (verbose){					
					System.err.println(
							"Tried to move " + readAtom + " from " + sourceID + " to " + targetID + ": " + rowsMoved);
				}
				if (rowsMoved > 0) {
					// The atom templates here don't contain wildcards -- each template can only move up to one row.
					break;
				}
			}
		}

		for (PslProblem problem : problems) {
			problemsToReadPartitions.get(problem).add(targetID);
			partitionsToProblems.put(targetID, problem);
		}
	}

	public void cleanUp(PslProblem problem) {
		// After the inference, move the atoms back to the standard partitions,
		// when possible.
		// TODO for the write partition (which isn't shared!), this could be more efficient 
		// atom args) (vbl)
		changeWritePartition(problem, STD_PARTITION);
		List<PslProblem> problems = new ArrayList<>();
		problems.add(problem);
		Set<Integer> originalSources = new HashSet<>(problemsToReadPartitions.get(problem));
		changeReadPartition(problems, problem.declareAtomsForReading(), STD_PARTITION);
		for (Integer originalSrc : originalSources) {
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

	public Integer getWritePartition(PslProblem problem){
		return problemsToWritePartitions.get(problem);
	}


	public Set<Integer> getReadPartitions(PslProblem problem){
		return problemsToReadPartitions.getSet(problem);
	}
	
	// For testing:

	public void printReadPartitionsPerProblem() {
		for (String s : getReadPartitionsPerProblem()) {
			System.out.println(s);
		}
	}

	public List<String> getReadPartitionsPerProblem() {
		List<String> readPartitionsPerProblem = new ArrayList<>();
		for (Entry<PslProblem, Collection<Integer>> entry : problemsToReadPartitions.entrySet()) {
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
		for (Entry<PslProblem, Integer> entry : problemsToWritePartitions.entrySet()) {
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
		for (Entry<Integer, Collection<PslProblem>> entry : partitionsToProblems.entrySet()) {
			problemsPerPartition.add(entry.getKey() + ": "
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
		int writePartition;
		Integer[] readPartitions;

		public ProblemWithPartitions(PslProblem problem) {
			this.problem = problem;
			this.writePartition = problemsToWritePartitions.get(problem);
			this.readPartitions = problemsToReadPartitions.get(problem).toArray(new Integer[0]);
		}

	}

	public class PartitionException extends Exception{

		private static final long serialVersionUID = -1077110774830553521L;

		public PartitionException(String message){
			super(message);
		}

	}

}

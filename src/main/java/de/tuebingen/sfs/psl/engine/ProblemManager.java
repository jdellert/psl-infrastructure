package de.tuebingen.sfs.psl.engine;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import org.linqs.psl.config.Config;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;

import de.tuebingen.sfs.psl.engine.PartitionManager.PartitionException;
import de.tuebingen.sfs.psl.engine.PartitionManager.ProblemWithPartitions;

public class ProblemManager {

	private PartitionManager partitionManager;
	private InferenceStore inferenceStore;
	private DatabaseManager dbManager;

	private int nextId = 1;

	// map from problemId to problem
	// TODO make sure the problem IDs are used the same way everywhere -- getNewId() vs problem.getName() (vbl)
	private Map<String, PslProblem> problems;

	public ProblemManager(PartitionManager partitionManager) {
		this.partitionManager = partitionManager;
		this.dbManager = partitionManager.getDbManager();
		this.inferenceStore = new InferenceStore();
		this.problems = new HashMap<>();
	}
	
	public static ProblemManager defaultProblemManager(){
		String suffix = System.getProperty("user.name") + "@" + PslProblem.getHostname();
		String baseDBPath = Config.getString("dbpath", System.getProperty("java.io.tmpdir"));
		String dbPath = Paths.get(baseDBPath, ProblemManager.class.getName() + "_" + suffix).toString();
		RDBMSDataStore dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbPath, true));
		DatabaseManager dbManager = new DatabaseManager(dataStore);
		PartitionManager partitionManager = new PartitionManager(dbManager);
		return new ProblemManager(partitionManager);
	}

	public String getNewId() {
		return Integer.toString(nextId++);
	}

	public String getNewId(String idSuffix) {
		return (nextId++) + "-" + idSuffix;
	}

	public String registerProblem(PslProblem problem) {
		String problemId = getNewId();
		registerProblem(problemId, problem);
		return problemId;
	}

	public void registerProblem(String problemId, PslProblem problem) {
		problems.put(problemId, problem);
		partitionManager.registerProblem(problem);
	}

	public PslProblem getProblem(String problemId) {
		return problems.get(problemId);
	}

	public InferenceResult getLastResult(String problemId) {
		return inferenceStore.getLastResult(problemId);
	}

	public RuleAtomGraph getLastRuleAtomGraph(String problemId) {
		return inferenceStore.getLastResult(problemId).getRag();
	}

	public Map<String, Double> getLastValueMap(String problemId) {
		return inferenceStore.getLastResult(problemId).getInferenceValues();
	}

	public void delete(PslProblem problem, String predName, String... args) {
		dbManager.deleteAtoms(predName, partitionManager.getWritePartitionID(problem),
				partitionManager.getReadPartitionIDs(problem), args);
	}
	
	// For tests/demos that only work with individual problems.
	public InferenceResult registerAndRunProblem(PslProblem problem){
		registerProblem(problem.getName(), problem);
		System.err.println("Preparing partitions.");
		ProblemWithPartitions problemWithPartitions = partitionManager.preparePartitions(problem);
		System.err.println("Partitions prepared.");
		dbManager.openDatabase(problemWithPartitions.problem.getName(), problemWithPartitions.writePartition,
				problemWithPartitions.problem.getClosedPredicates(), problemWithPartitions.readPartitions);
		InferenceResult result = null;
		try {
			System.err.println("Starting inference.");
			result = problem.call();
			System.err.println("Inference finished (" + problem.getName() + ")");
//			inferenceStore.add(problem.getName(), result);
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			partitionManager.cleanUp(problem);
		}
		dbManager.closeDatabase(problem.getName());
		return result;
	}

	public boolean preparePartitionsAndRun(List<PslProblem> problems) {
		boolean success = true;
		try {
			List<ProblemWithPartitions> problemsWithPartitions = partitionManager.preparePartitions(problems);
			runParallelProblems(problemsWithPartitions);
		} catch (PartitionException e) {
			System.err.println(e.getMessage());
			success = false;
		}
		finally {
			for (PslProblem problem : problems)
				partitionManager.cleanUp(problem);
		}
		return success;
	}

	private void runParallelProblems(List<ProblemWithPartitions> problemsWithPartitions) {
		FutureTask<InferenceResult>[] tasks = new FutureTask[problemsWithPartitions.size()];

		for (int i = 0; i < tasks.length; i++) {
			ProblemWithPartitions problemWithPartitions = problemsWithPartitions.get(i);
			dbManager.openDatabase(problemWithPartitions.problem.getName(), problemWithPartitions.writePartition,
					problemWithPartitions.problem.getClosedPredicates(), problemWithPartitions.readPartitions);
			tasks[i] = new FutureTask<>(problemWithPartitions.problem);
			Thread t = new Thread(tasks[i]);
			t.start();
		}

		for (int i = 0; i < tasks.length; i++) {
			FutureTask<InferenceResult> taskResult = tasks[i];
			PslProblem problem = problemsWithPartitions.get(i).problem;
			try {
				InferenceResult result = taskResult.get();
				System.err.println("Inference finished (" + problem.getName() + ")");
				inferenceStore.add(problem.getName(), result);
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
			dbManager.closeDatabase(problem.getName());
		}
	}

	public PartitionManager getPartitionManager() {
		return partitionManager;
	}

	public InferenceStore getInferenceStore() {
		return inferenceStore;
	}

	public DatabaseManager getDbManager() {
		return dbManager;
	}
}

/*
 * Copyright 2018–2022 University of Tübingen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tuebingen.sfs.psl.engine;

import de.tuebingen.sfs.psl.engine.PartitionManager.PartitionException;
import de.tuebingen.sfs.psl.engine.PartitionManager.ProblemWithPartitions;
import de.tuebingen.sfs.psl.util.log.InferenceLogger;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class ProblemManager {

    public static final int MAX_PROBLEM_ID_LENGTH = 255;
    private PartitionManager partitionManager;
    private InferenceStore inferenceStore;
    private DatabaseManager dbManager;
    private int nextId = 1;

    // map from problemId to problem
    // TODO make sure the problem IDs are used the same way everywhere -- getNewId() vs problem.getName()
    private Map<String, PslProblem> problems;

    public ProblemManager(PartitionManager partitionManager) {
        this.partitionManager = partitionManager;
        this.dbManager = partitionManager.getDbManager();
        this.inferenceStore = new InferenceStore();
        this.problems = new HashMap<>();
    }

    public static ProblemManager defaultProblemManager() {
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

    public void cleanUpProblem(String problemId){
        PslProblem problem = problems.get(problemId);
        if(problem!=null){
            Set<AtomTemplate> atomsToDelete = problem.declareAtomsForCleanUp();
            if(atomsToDelete.size() > 0){
                for(AtomTemplate at : atomsToDelete){
                    dbManager.deleteAtomsForProblem(at.getPredicateName(), problemId, at);
                }
            }
        }
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

    // For tests/demos that only work with individual problems.
    public InferenceResult registerAndRunProblem(PslProblem problem) {
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
            inferenceStore.add(problem.getName(), result);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            partitionManager.cleanUp(problem);
        }
        dbManager.closeDatabase(problem.getName());
        return result;
    }

    public boolean preparePartitionsAndRun(List<PslProblem> problems) {
        return preparePartitionsAndRun(problems, partitionManager.stdLogger);
    }

    public boolean preparePartitionsAndRun(List<PslProblem> problems, InferenceLogger logger) {
        boolean success = true;
        try {
            List<ProblemWithPartitions> problemsWithPartitions = partitionManager.preparePartitions(problems, logger);
            runParallelProblems(problemsWithPartitions);
        } catch (PartitionException e) {
            System.err.println(e.getMessage());
            success = false;
        } finally {
            for (PslProblem problem : problems)
                partitionManager.cleanUp(problem, logger);
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

    public InferenceResult runAndGetAverageResult(PslProblem problem, int numRepetitions){
        List<InferenceResult> inferenceResults = runRepeatedly(problem, numRepetitions);
        Map<String, Double> averageValues = new TreeMap<>();
        Map<String, List<Double>> records = new TreeMap<>();

//        List<Double> scores = new ArrayList<>();
        //rag: first problem
        for(InferenceResult inferenceResult: inferenceResults){
//            System.out.println("Get new result: ");
            inferenceResult.getInferenceValues().entrySet().forEach(entry -> {
//                if(entry.getKey().contains("x(")) {
//                    System.out.println(entry.getKey() + " " + entry.getValue());
//                }
                double value;
                if(!averageValues.containsKey(entry.getKey())){
                    List<Double> newRecord = new ArrayList<>();
                    newRecord.add(entry.getValue());
                    records.put(entry.getKey(), newRecord);
                    value = entry.getValue();
                } else {
                    value = averageValues.get(entry.getKey()) + entry.getValue();
                    // need another map with atom key and value list as value,
                    records.get(entry.getKey()).add(entry.getValue());
                }
                averageValues.put(entry.getKey(), value);
            });
//            System.out.println("End of result");
//            System.out.println("Score check:" + inferenceResult.getScore());
//            scores.add(inferenceResult.getScore());
        }
//        int i = scores.indexOf(Collections.min(scores));
//        System.out.println(i);
        averageValues.entrySet().forEach(entry -> {

            if(entry.getKey().contains("dr(") || entry.getKey().contains("fx(")) { //
                System.out.println(entry.getKey()); //+ " replace old value " + entry.getValue()
//                Collections.sort(records.get(entry.getKey()));
                System.out.println(records.get(entry.getKey()));
                System.out.println(Collections.min(records.get(entry.getKey())));
                System.out.println(Collections.max(records.get(entry.getKey())));

            }
            // print all atoms
//            System.out.println(entry.getKey());
//            System.out.println(records.get(entry.getKey()));
//            System.out.println(Collections.min(records.get(entry.getKey())));
//            System.out.println(Collections.max(records.get(entry.getKey())));

            entry.setValue(entry.getValue()/numRepetitions);
            if(entry.getKey().contains("dr(") || entry.getKey().contains("fx(")) System.out.println("new value: " + entry.getValue());
        });
//        Map<String, Double> inferenceValue = inferenceResults.get(0).getInferenceValues();

        return new InferenceResult(inferenceResults.get(0).getRag(), averageValues);
    }

    public InferenceResult runAndGetBestResult(PslProblem problem, int numRepetitions){
        List<InferenceResult> inferenceResults = runRepeatedly(problem, numRepetitions);
        List<Double> scores = new ArrayList<>();
        for(InferenceResult inferenceResult: inferenceResults){
            System.out.println("Score check:" + inferenceResult.getScore());
            scores.add(inferenceResult.getScore());
        }
        int i = scores.indexOf(Collections.min(scores));
        System.out.println(i);
        return inferenceResults.get(i);
    }

    private List<InferenceResult> runRepeatedly(PslProblem problem, int numRepetitions){
        List<InferenceResult> inferenceResults = new ArrayList<>();
        if(numRepetitions <= 0){
            numRepetitions = 1;
        }
        for(int i = 0; i < numRepetitions; i++){
            inferenceResults.add(registerAndRunProblem(problem));
        }
        return inferenceResults;
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

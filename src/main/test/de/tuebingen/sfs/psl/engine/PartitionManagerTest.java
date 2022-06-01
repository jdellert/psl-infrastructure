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

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

import static de.tuebingen.sfs.psl.engine.PartitionManager.STD_PARTITION_ID;

public class PartitionManagerTest extends TestCase {

    private PartitionManager partitionManager;
    private DemoPslProblem prob1;
    private DemoPslProblem prob2;
    private List<PslProblem> problems;
    private DatabaseManager dbManager;
    private ProblemManager problemManager;

    @Override
    protected void setUp() throws Exception {
        problemManager = ProblemManager.defaultProblemManager();
        partitionManager = problemManager.getPartitionManager();
        dbManager = partitionManager.getDbManager();

        prob1 = new DemoPslProblem(dbManager, "ProblemA", "OpenPredA", "ClosedPred");
        prob1.declarePredicates();
        prob1.pregenerateAtoms();
        prob1.addDemoObservation("1", "1");
        problemManager.registerProblem(prob1);

        prob2 = new DemoPslProblem(dbManager, "ProblemB", "OpenPredB", "ClosedPred");
        prob2.declarePredicates();
        prob2.pregenerateAtoms();
        prob2.addDemoObservation("2", "2");
        problemManager.registerProblem(prob2);

        problems = new ArrayList<>();
        problems.add(prob1);
        problems.add(prob2);

    }

    private void initialPartitions() {
        List<String> problemsPerPartition = partitionManager.getProblemsPerPartition();
        assertEquals(1, problemsPerPartition.size());
        String firstPartition = problemsPerPartition.get(0);
        assertEquals(STD_PARTITION_ID, firstPartition.substring(0, firstPartition.indexOf(':')));
        assertEquals(3, firstPartition.split("\\s+").length);
        assertTrue(firstPartition.contains("ProblemA"));
        assertTrue(firstPartition.contains("ProblemB"));

        List<String> readPartitionsPerProblem = partitionManager.getReadPartitionsPerProblem();
        assertEquals(2, readPartitionsPerProblem.size());
        String problemA = readPartitionsPerProblem.get(0);
        String problemB = readPartitionsPerProblem.get(1);
        if (problemA.contains("ProblemB")) {
            problemA = readPartitionsPerProblem.get(1);
            problemB = readPartitionsPerProblem.get(0);
        }
        assertEquals("[Partition[" + STD_PARTITION_ID + "]]", problemA.substring(problemA.indexOf(':') + 2));
        assertEquals("[Partition[" + STD_PARTITION_ID + "]]", problemB.substring(problemB.indexOf(':') + 2));

        List<String> writePartitionsPerProblem = partitionManager.getWritePartitionsPerProblem();
        assertEquals(2, writePartitionsPerProblem.size());
        problemA = writePartitionsPerProblem.get(0);
        problemB = writePartitionsPerProblem.get(1);
        if (problemA.contains("ProblemB")) {
            problemA = writePartitionsPerProblem.get(1);
            problemB = writePartitionsPerProblem.get(0);
        }
        assertEquals(": Partition[" + STD_PARTITION_ID + "]", problemA.substring(problemA.indexOf(':')));
        assertEquals(": Partition[" + STD_PARTITION_ID + "]", problemB.substring(problemB.indexOf(':')));
    }

    public void testInitialPartitioning() {
        System.out.println("\ntestInitialPartitioning");
        System.out.println("==============================");
        partitionManager.printPartitionSummary();
        System.out.println("==============================\n");

        initialPartitions();
    }

    public void testPartitionsAfterRunning() {
        problemManager.preparePartitionsAndRun(problems);
        System.out.println("\ntestPartitionsAfterRunning");
        System.out.println("==============================");
        partitionManager.printPartitionSummary();
        System.out.println("==============================\n");

        List<String> problemsPerPartition = partitionManager.getProblemsPerPartition();
        assertEquals(5, problemsPerPartition.size());
        String firstPartition = problemsPerPartition.get(0);
        assertEquals('2', firstPartition.charAt(0));
        int nPartitionsOnlyForA = 0;
        int nPartitionsOnlyForB = 0;
        int nPartitionsForBoth = 0;
        for (String s : problemsPerPartition) {
            if (s.contains("ProblemA")) {
                if (s.contains("ProblemB")) {
                    nPartitionsForBoth++;
                    continue;
                }
                nPartitionsOnlyForA++;
            } else if (s.contains("ProblemB")) {
                nPartitionsOnlyForB++;
            }
        }
        assertEquals(2, nPartitionsOnlyForA);
        assertEquals(2, nPartitionsOnlyForB);
        assertEquals(1, nPartitionsForBoth);

        List<String> readPartitionsPerProblem = partitionManager.getReadPartitionsPerProblem();
        assertEquals(2, readPartitionsPerProblem.size());
        String problemA = readPartitionsPerProblem.get(0);
        String problemB = readPartitionsPerProblem.get(1);
        if (problemA.contains("ProblemB")) {
            problemA = readPartitionsPerProblem.get(1);
            problemB = readPartitionsPerProblem.get(0);
        }
        assertEquals(3, problemA.split("\\s+").length);
        assertTrue(
                (problemA.contains("4") && problemA.contains("5")) || (problemA.contains("4") && problemA.contains("6"))
                        || (problemA.contains("5") && problemA.contains("6")));
        assertEquals(3, problemB.split("\\s+").length);
        assertTrue(
                (problemB.contains("4") && problemB.contains("5")) || (problemB.contains("4") && problemB.contains("6"))
                        || (problemB.contains("5") && problemB.contains("6")));

        List<String> writePartitionsPerProblem = partitionManager.getWritePartitionsPerProblem();
        assertEquals(2, writePartitionsPerProblem.size());
        problemA = writePartitionsPerProblem.get(0);
        problemB = writePartitionsPerProblem.get(1);
        if (problemA.contains("ProblemB")) {
            problemA = writePartitionsPerProblem.get(1);
            problemB = writePartitionsPerProblem.get(0);
        }
        assertEquals("ProblemA: 2", problemA);
        assertEquals("ProblemB: 3", problemB);
    }

    public void testPartitionsAfterCleanup() {
        problemManager.preparePartitionsAndRun(problems);
        partitionManager.cleanUp(prob1);

        System.out.println("\ntestPartitionsAfterCleanup");
        System.out.println("==============================");

        partitionManager.printPartitionSummary();
        // TODO make this into an actual test
        dbManager.printTable("ClosedPred");

        System.out.println();
        partitionManager.cleanUp(prob2);
        System.out.println("Cleaned up prob2");
        System.out.println("===");
        partitionManager.printPartitionSummary();
        // TODO make this into an actual test
        dbManager.printTable("ClosedPred");

        System.out.println("==============================\n");

        initialPartitions();

    }

}

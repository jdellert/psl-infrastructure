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
package de.tuebingen.sfs.psl.examples.livesknows;

import de.tuebingen.sfs.psl.engine.IdeaGenerator;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class SampleIdeaGenerator extends IdeaGenerator {

    public SampleIdeaGenerator(SamplePslProblem problem) {
        super(problem);
    }

    public void generateAtoms(String filePath) {
        Set<String> persons = new HashSet<>();

        // Add what we know about who lives where:
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(new File(getClass().getResource(filePath).toURI())),
                        StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isBlank()) continue;
                try {
                    String[] cells = line.strip().split("\\s+");
                    String person = cells[0];
                    String address = cells[1];
                    double value = Double.parseDouble(cells[2]);
                    // Add this as a fixed ("observed") atom value:
                    pslProblem.addObservation(LivesPred.NAME, value, person, address);
                    persons.add(person);
                } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
                }
            }
        } catch (IOException | URISyntaxException ignored) {
        }

        // Add open groundings for the Knows predicate:
        for (String person1 : persons) {
            for (String person2 : persons) {
                if (person1.equals(person2)) {
                    continue;
                }
                // Add this as a "target"---an atom whose value we want to infer
                pslProblem.addTarget(KnowsPred.NAME, person1, person2);
            }
        }
    }
}

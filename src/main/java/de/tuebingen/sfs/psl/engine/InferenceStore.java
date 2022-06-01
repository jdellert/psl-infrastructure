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

import de.tuebingen.sfs.psl.util.data.Multimap;
import de.tuebingen.sfs.psl.util.data.Multimap.CollectionType;

import java.util.List;

public class InferenceStore {

    private Multimap<String, InferenceResult> map;

    public InferenceStore() {
        this.map = new Multimap<>(CollectionType.LIST);
    }

    public void add(String problemId, InferenceResult inferenceResult) {
        map.put(problemId, inferenceResult);
        System.err.println("Added inference result for " + problemId);
    }

    public List<InferenceResult> get(String problemId) {
        return map.getList(problemId);
    }

    public InferenceResult getLastResult(String problemId) {
    	System.err.println("Retrieving result for " + problemId);
        List<InferenceResult> results = get(problemId);
        if (results == null || results.isEmpty())
            return null;
        return results.get(results.size() - 1);
    }

}

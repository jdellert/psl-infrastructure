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

package de.tuebingen.sfs.psl.engine;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class InferenceResult {

	// Actual inference results:
	private RuleAtomGraph rag;
	private Map<String, Double> inferenceValues;
	// Inference configuration:
	private Set<String> languages;
	// TODO which other details to save? (vbl)

	public InferenceResult(RuleAtomGraph rag, Map<String, Double> inferenceValues, Set<String> languages) {
		this.rag = rag;
		this.inferenceValues = inferenceValues;
		this.languages = languages;
	}

	public InferenceResult(RuleAtomGraph rag, Map<String, Double> inferenceValues) {
		this(rag, inferenceValues, null);
	}

	public InferenceResult(RuleAtomGraph rag) {
		this(rag, rag.getRagFilter().getBeliefValues(), null);
	}

	public InferenceResult(Map<String, Double> inferenceValues) {
		this(null, inferenceValues, null);
	}

	public RuleAtomGraph getRag() {
		return rag;
	}

	public void setRag(RuleAtomGraph rag) {
		this.rag = rag;
	}

	public Map<String, Double> getInferenceValues() {
		return inferenceValues;
	}

	public void setInferenceValues(Map<String, Double> inferenceValues) {
		this.inferenceValues = inferenceValues;
	}

	public Set<String> getLanguages() {
		return languages;
	}

	public void setLanguages(Set<String> languages) {
		this.languages = languages;
	}

	public String toString() {
		return "InferenceResult[RAG: " + rag + ", inferenceValues: " + inferenceValues + " | CONFIG: languages: "
				+ languages + "]";
	}
	
	public void printInferenceValues(){
		for (Entry<String, Double> entry : inferenceValues.entrySet()){
			System.out.println(entry.getKey() + "\t" + entry.getValue());
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((inferenceValues == null) ? 0 : inferenceValues.hashCode());
		result = prime * result + ((languages == null) ? 0 : languages.hashCode());
		result = prime * result + ((rag == null) ? 0 : rag.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof InferenceResult)) {
			return false;
		}
		InferenceResult other = (InferenceResult) obj;
		if (inferenceValues == null) {
			if (other.inferenceValues != null) {
				return false;
			}
		} else if (!inferenceValues.equals(other.inferenceValues)) {
			return false;
		}
		if (languages == null) {
			if (other.languages != null) {
				return false;
			}
		} else if (!languages.equals(other.languages)) {
			return false;
		}
		if (rag == null) {
			if (other.rag != null) {
				return false;
			}
		} else if (!rag.equals(other.rag)) {
			return false;
		}
		return true;
	}

}

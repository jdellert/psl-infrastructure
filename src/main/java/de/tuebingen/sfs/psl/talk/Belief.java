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
package de.tuebingen.sfs.psl.talk;

public class Belief {
    private double threshold;
    private String adjective;
    private String predicate;
    private String predicateMinInf;
    private String predicateOnly;
    private String predicateMaxInf;
    private String adverb;
    private String adjectiveHigh;
    private String similarity;
    private String similarityMinInf;
    private String similarityOnly;
    private String similarityMaxInf;
    private String frequencyAdv;
    private String frequencyAdj;

    public Belief(double threshold, String adjective, String predicate, String predicateMinInf, String predicateOnly,
                  String predicateMaxInf, String adverb, String adjectiveHigh, String similarity,
                  String similarityMinInf, String similarityOnly, String similarityMaxInf, String frequencyAdv,
                  String frequencyAdj) {
        this.threshold = threshold;
        this.adjective = adjective;
        this.predicate = predicate;
        this.adverb = adverb;
        this.predicateMinInf = predicateMinInf;
        this.predicateOnly = predicateOnly;
        this.predicateMaxInf = predicateMaxInf;
        this.adjectiveHigh = adjectiveHigh;
        this.similarity = similarity;
        this.similarityMinInf = similarityMinInf;
        this.similarityOnly = similarityOnly;
        this.similarityMaxInf = similarityMaxInf;
        this.frequencyAdv = frequencyAdv;
        this.frequencyAdj = frequencyAdj;
    }

    /**
     * @return the threshold
     */
    public double getThreshold() {
        return threshold;
    }

    /**
     * @param threshold the threshold to set
     */
    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    /**
     * @return the adjective
     */
    public String getAdjective() {
        return adjective;
    }

    /**
     * @param adjective the adjective to set
     */
    public void setAdjective(String adjective) {
        this.adjective = adjective;
    }

    /**
     * @return the predicate
     */
    public String getPredicate() {
        return predicate;
    }

    public String getPredicateMinInf() {
        return predicateMinInf;
    }

    public String getPredicateOnly() {
        return predicateOnly;
    }

    public String getPredicateMaxInf() {
        return predicateMaxInf;
    }

    /**
     * @param predicate the predicate to set
     */
    public void setPredicate(String predicate) {
        this.predicate = predicate;
    }

    /**
     * @return the adverb
     */
    public String getAdverb() {
        return adverb;
    }

    /**
     * @param adverb the adverb to set
     */
    public void setAdverb(String adverb) {
        this.adverb = adverb;
    }

    /**
     * @return the adjectiveHigh
     */
    public String getAdjectiveHigh() {
        return adjectiveHigh;
    }

    /**
     * @param adjectiveHigh the adjectiveHigh to set
     */
    public void setAdjectiveHigh(String adjectiveHigh) {
        this.adjectiveHigh = adjectiveHigh;
    }

    /**
     * @return the similarity
     */
    public String getSimilarity() {
        return similarity;
    }

    /**
     * @return the minimum similarity (infinitive)
     */
    public String getSimilarityMinInf() {
        return similarityMinInf;
    }

    public String getSimilarityOnly() {
        return similarityOnly;
    }

    public String getSimilarityMaxInf() {
        return similarityMaxInf;
    }

    /**
     * @param similarity the similarity to set
     */
    public void setSimilarity(String similarity) {
        this.similarity = similarity;
    }

    /**
     * @return the frequencyAdv
     */
    public String getFrequencyAdverb() {
        return frequencyAdv;
    }

    /**
     * @param frequencyAdv the frequencyAdv to set
     */
    public void setFrequencyAdverb(String frequencyAdv) {
        this.frequencyAdv = frequencyAdv;
    }

    /**
     * @return the frequencyAdj
     */
    public String getFrequencyAdjective() {
        return frequencyAdj;
    }

    /**
     * @param frequencyAdj the frequencyAdj to set
     */
    public void setFrequencyAdjective(String frequencyAdj) {
        this.frequencyAdj = frequencyAdj;
    }

}

package de.tuebingen.sfs.psl.talk;

public class Belief {
    private double threshold;
    private String adjective;
    private String predicate;
    private String adverb;
    private String adjectiveHigh;
    private String similarity;
    private String frequencyAdv;
    private String frequencyAdj;

    public Belief(double threshold, String adjective, String predicate, String adverb, String adjectiveHigh,
                  String similarity, String frequencyAdv, String frequencyAdj) {
        this.threshold = threshold;
        this.adjective = adjective;
        this.predicate = predicate;
        this.adverb = adverb;
        this.adjectiveHigh = adjectiveHigh;
        this.similarity = similarity;
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

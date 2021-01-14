package de.tuebingen.sfs.psl.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Objects;

public class PslProblemConfig {

    private String name;
    private boolean declareUserPrior;

    private DatabaseManager dbManager;

    public PslProblemConfig() {
        resetToDefaults();
    }

    public PslProblemConfig(String name, boolean declareUserPrior, DatabaseManager dbManager) {
        this.name = name;
        this.declareUserPrior = declareUserPrior;
        this.dbManager = dbManager;
    }

    public void resetToDefaults() {
        name = null;
        declareUserPrior = false;
        dbManager = null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDeclareUserPrior() {
        return declareUserPrior;
    }

    public void setDeclareUserPrior(boolean declareUserPrior) {
        this.declareUserPrior = declareUserPrior;
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    public void setDbManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    protected void copyFields(PslProblemConfig newConfig) {
        newConfig.setName(name);
        newConfig.setDeclareUserPrior(declareUserPrior);
        newConfig.setDbManager(dbManager);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PslProblemConfig)) return false;

        PslProblemConfig that = (PslProblemConfig) o;

        if (declareUserPrior != that.declareUserPrior) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (declareUserPrior ? 1 : 0);
        return result;
    }

    private static final String NAME_FIELD = "name";
    private static final String USER_PRIOR_FIELD = "declareUserPrior";

    public ObjectNode toJson() {
        return toJson(new ObjectMapper());
    }

    /**
     * Convert all persistable fields into a flat Json node.
     *
     * !! Override in subclass with:
     * {@code
     * ObjectNode rootNode = super.toJson(mapper);
     * rootNode.set(... additional fields ...);
     * }
     */
    public ObjectNode toJson(ObjectMapper mapper) {
        ObjectNode rootNode = mapper.createObjectNode();
        try {
            rootNode.set(NAME_FIELD, mapper.readTree(mapper.writeValueAsString(name)));
            rootNode.set(USER_PRIOR_FIELD, mapper.readTree(mapper.writeValueAsString(declareUserPrior)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rootNode;
    }

    /**
     * Set all persistable fields from flat Json node.
     *
     * !! Override in subclass to parse additional fields!
     */
    public void setFromJson(ObjectMapper mapper, JsonNode rootNode) {
        try {
            setName(mapper.treeToValue(rootNode.path(NAME_FIELD), String.class));
            setDeclareUserPrior(mapper.treeToValue(rootNode.path(USER_PRIOR_FIELD), boolean.class));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

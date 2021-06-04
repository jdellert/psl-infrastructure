package de.tuebingen.sfs.psl.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tuebingen.sfs.psl.util.log.InferenceLogger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Objects;
import java.util.function.Consumer;

public class PslProblemConfig {

	private static final String NAME_FIELD = "name";
	private static final String USER_PRIOR_FIELD = "declareUserPrior";
	private String name;
	private boolean declareUserPrior;
	private DatabaseManager dbManager;
	private String logfilePath;
	private InferenceLogger logger;

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

		logger = new InferenceLogger();
		setLogfile("src/test/resources/inf-log.txt");
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
		if (this == o)
			return true;
		if (!(o instanceof PslProblemConfig))
			return false;

		PslProblemConfig that = (PslProblemConfig) o;

		if (declareUserPrior != that.declareUserPrior)
			return false;
		return Objects.equals(name, that.name);
	}

	@Override
	public int hashCode() {
		int result = name != null ? name.hashCode() : 0;
		result = 31 * result + (declareUserPrior ? 1 : 0);
		return result;
	}

	public ObjectNode toJson() {
		return toJson(new ObjectMapper());
	}

	/**
	 * Convert all persistable fields into a flat Json node.
	 * <p>
	 * !! Override in subclass with: {@code
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
	 * <p>
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

	public Consumer<String> getGuiMessager() {
		return logger.getGuiStream();
	}

	public void setGuiMessager(Consumer<String> messager) {
		logger.setGuiStream(messager);
	}

	public boolean setLogfile(String logfilePath) {
		this.logfilePath = logfilePath;
		if (this.logfilePath == null || this.logfilePath.isEmpty())
			logger.setLogStream(System.err);
		else {
			try {
				PrintStream logStream = new PrintStream(this.logfilePath, "UTF-8");
				logger.setLogStream(logStream);
			} catch (FileNotFoundException | UnsupportedEncodingException e) {
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}

	public String getLogfilePath() {
		return logfilePath;
	}

	public InferenceLogger getLogger() {
		return logger;
	}

}

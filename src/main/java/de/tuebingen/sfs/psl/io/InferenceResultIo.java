package de.tuebingen.sfs.psl.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tuebingen.sfs.psl.engine.InferenceResult;
import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.engine.RagFilter;
import de.tuebingen.sfs.psl.engine.RuleAtomGraph;
import de.tuebingen.sfs.psl.talk.TalkingPredicate;
import de.tuebingen.sfs.psl.talk.TalkingRule;
import de.tuebingen.sfs.psl.util.data.Tuple;

public class InferenceResultIo {

	public static String INFERENCE_RESULT_PATH = "src/test/resources/serialization/inference-result.txt";

	private static enum TSV_SECTION {
		ATOMS, GROUNDINGS, TALKING_PREDS, SCORES
	}

	public static void saveToFile(InferenceResult inferenceResult, PslProblem problem, ObjectMapper mapper) {
		saveToFile(inferenceResult, problem.getTalkingPredicates(), problem.getTalkingRules(), mapper);
	}

	public static void saveToFile(InferenceResult inferenceResult, PslProblem problem, ObjectMapper mapper, File path) {
		saveToFile(inferenceResult, problem.getTalkingPredicates(), problem.getTalkingRules(), mapper, path);
	}

	public static void saveToFile(InferenceResult inferenceResult, Map<String, TalkingPredicate> talkingPreds,
			Map<String, TalkingRule> talkingRules, ObjectMapper mapper) {
		saveToFile(inferenceResult, talkingPreds, talkingRules, mapper, new File(INFERENCE_RESULT_PATH));
	}

	public static void saveToFile(InferenceResult inferenceResult, Map<String, TalkingPredicate> talkingPreds,
								  Map<String, TalkingRule> talkingRules, ObjectMapper mapper, File path) {
		System.out.println("PATH");
		System.out.println(path);
		System.out.println(path.exists());
		if (!path.exists()) {
			path.getParentFile().mkdirs();
		}

		try {
			saveToFile(inferenceResult, talkingPreds, talkingRules, mapper, new FileOutputStream(path));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void saveToFile(InferenceResult inferenceResult, Map<String, TalkingPredicate> talkingPreds,
			Map<String, TalkingRule> talkingRules, ObjectMapper mapper, OutputStream out) throws IOException {
		StringBuilder sb = new StringBuilder();
		RuleAtomGraph rag = inferenceResult.getRag();
		RagFilter filter = rag.getRagFilter();
		sb.append("RAG FILTER\n===============");
		sb.append("\nCLASS\t").append(filter.getClass().getName());
		sb.append("\nIGNORE LIST\t").append(filter.getIgnoreList());
		sb.append("\nIGNORE IN GUI\t").append(filter.getIgnoreInGui());
		sb.append("\nPREVENT USER INTERACTION\t").append(filter.getPreventUserInteraction());
		sb.append("\nPRED TO NAME\t").append(filter.getGroundPred2ActualNames());

		sb.append("\n\n\nRULE GROUNDINGS\n===============\nGROUNDING\tSTATUS\tINCOMING\tCLASS\tPARAMETERS\n");
		for (String grounding : rag.getGroundingNodes()) {
			sb.append(grounding).append("\t").append(rag.getGroundingStatus(grounding)).append("\t");
			sb.append(rag.getIncomingLinks(grounding).stream().map(x -> x.get(0)).collect(Collectors.toList()));
			TalkingRule rule = talkingRules.get(grounding.substring(0, grounding.indexOf('[')));
			sb.append("\t").append(rule.getClass().getName()).append("\t").append(rule.getSerializedParameters())
					.append("\n");
		}
		sb.append("\n\nEQUALITY RULES\n===============\n").append(rag.getEqualityGroundings());
		sb.append("\n\nATOM LINKS\n===============\n");
		sb.append("ATOM\tBELIEF VALUE\tATOM STATUS\tGROUNDING\tLINK STATUS\tLINK STRENGTH\tLINK PRESSURE\n");
		for (String atom : rag.getAtomNodes()) {
			for (Tuple link : rag.getOutgoingLinks(atom)) {
				sb.append(atom).append("\t").append(filter.getValueForAtom(atom)).append("\t")
						.append(rag.getAtomStatus(atom)).append("\t");
				sb.append(link.get(1)).append("\t").append(rag.getLinkStatus(link)).append("\t");
				sb.append(rag.getLinkStrength(link)).append("\t").append(rag.getLinkPressure(link)).append("\n");
			}
		}
		sb.append("\n\nINFERENCE VALUES\n===============\n");
		sb.append("ATOM\tBELIEF VALUE\n");
		for (Entry<String, Double> entry : inferenceResult.getInferenceValues().entrySet()) {
			sb.append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
		}
		sb.append("\n\nTALKING PREDICATE CLASSES\n===============\nNAME\tARITY\tCLASS\n");
		for (Entry<String, TalkingPredicate> entry : talkingPreds.entrySet()) {
			sb.append(entry.getKey()).append("\t").append(entry.getValue().getArity()).append("\t")
					.append(entry.getValue().getClass().getName()).append("\n");
		}

		out.write(sb.toString().getBytes());
	}

	public static InferenceResult fromFile(ObjectMapper mapper, Map<String, TalkingPredicate> talkingPreds,
			Map<String, TalkingRule> talkingRules) {
		return fromFile(mapper, INFERENCE_RESULT_PATH, talkingPreds, talkingRules);
	}

	public static InferenceResult fromFile(ObjectMapper mapper, String path, Map<String, TalkingPredicate> talkingPreds,
			Map<String, TalkingRule> talkingRules) {
		try {
			return fromFile(mapper, new FileInputStream(path), talkingPreds, talkingRules);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	@SuppressWarnings("rawtypes")
	public static InferenceResult fromFile(ObjectMapper mapper, InputStream is,
										   Map<String, TalkingPredicate> talkingPreds, Map<String, TalkingRule> talkingRules) {
		return fromFile(mapper, is, talkingPreds, talkingRules, true);
	}

	@SuppressWarnings("rawtypes")
	public static InferenceResult fromFile(ObjectMapper mapper, InputStream is,
			Map<String, TalkingPredicate> talkingPreds, Map<String, TalkingRule> talkingRules, boolean closeStream) {
		if (talkingPreds == null)
			talkingPreds = new TreeMap<>();
		if (talkingRules == null)
			talkingRules = new TreeMap<>();

		Pattern atomPattern = Pattern.compile("\\w{4,}\\([^\\(]+\\)");

		String filterClass = null;
		Set<String> ignoreList = new TreeSet<>();
		Set<String> ignoreInGui = new TreeSet<>();
		Set<String> preventUserInteraction = new TreeSet<>();
		Map<String, String> groundPreds2ActualNames = new TreeMap<>();

		Set<String> groundingNodes = new TreeSet<String>();
		Map<String, Double> groundingStatus = new TreeMap<>();
		Set<String> equalityGroundings = new TreeSet<>();
		Set<String> atomNodes = new TreeSet<String>();
		Map<String, Double> beliefValues = new TreeMap<>();
		Map<String, String> atomStatus = new TreeMap<>();
		Set<Tuple> links = new TreeSet<>();
		Map<Tuple, String> linkStatus = new TreeMap<>();
		Map<Tuple, Boolean> linkPressure = new TreeMap<>();
		Map<Tuple, Double> linkStrength = new TreeMap<>();
		Map<String, Set<Tuple>> outgoingLinks = new TreeMap<>();
		Map<String, List<Tuple>> incomingLinks = new TreeMap<>();
		Map<String, Double> scoreMap = new TreeMap<>();

		BufferedReader br = null;
		String line = "";
		TSV_SECTION current = null;
		try {
			InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
			br = new BufferedReader(isr);
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (line.equalsIgnoreCase("RAG FILTER")) {
					br.readLine(); // ====
					for (int i = 0; i < 5; i++) {
						line = br.readLine().trim();
						if (line.startsWith("CLASS")) {
							filterClass = line.split("\t")[1];
						} else if (line.startsWith("IGNORE LIST")) {
							line = line.split("\t")[1].trim();
							// Remove [ ]
							line = line.substring(1, line.length() - 1);
							if (line.isEmpty()) {
								continue;
							}
							for (String item : line.split(",")) {
								ignoreList.add(item.trim());
							}
						} else if (line.startsWith("IGNORE IN GUI")) {
							line = line.split("\t")[1];
							// Remove [ ]
							line = line.substring(1, line.length() - 1);
							if (line.isEmpty()) {
								continue;
							}
							for (String item : line.split(",")) {
								ignoreInGui.add(item.trim());
							}
						} else if (line.startsWith("PREVENT USER INTERACTION")) {
							line = line.split("\t")[1];
							// Remove [ ]
							line = line.substring(1, line.length() - 1);
							if (line.isEmpty()) {
								continue;
							}
							for (String item : line.split(",")) {
								preventUserInteraction.add(item.trim());
							}
						} else if (line.startsWith("PRED")) {
							line = line.split("\t")[1];
							// Remove { }
							line = line.substring(1, line.length() - 1);
							if (line.isEmpty()) {
								continue;
							}
							for (String item : line.split(",")) {
								String[] entry = item.split("=");
								groundPreds2ActualNames.put(entry[0].trim(), entry[1].trim());
							}
						}
					}
					continue;
				}
				if (line.equalsIgnoreCase("RULE GROUNDINGS")) {
					br.readLine(); // ====
					br.readLine(); // table header
					current = TSV_SECTION.GROUNDINGS;
					continue;
				}
				if (line.equalsIgnoreCase("ATOM LINKS")) {
					br.readLine(); // ====
					br.readLine(); // table header
					current = TSV_SECTION.ATOMS;
					continue;
				}
				if (line.equalsIgnoreCase("INFERENCE VALUES")) {
					br.readLine(); // ====
					br.readLine(); // table header
					current = TSV_SECTION.SCORES;
					continue;
				}
				if (line.equalsIgnoreCase("TALKING PREDICATE CLASSES")) {
					br.readLine(); // ====
					br.readLine(); // table header
					current = TSV_SECTION.TALKING_PREDS;
					continue;
				}
				if (line.equalsIgnoreCase("EQUALITY RULES")) {
					br.readLine();
					line = br.readLine().trim();
					line.substring(1, line.length() - 1);
					for (String grounding : line.split(",")) {
						equalityGroundings.add(grounding.trim());
					}
					continue;
				}
				String[] fields = line.split("\t");
				switch (current) {
				case GROUNDINGS:
					String grounding = fields[0].trim();
					groundingNodes.add(grounding);
					groundingStatus.put(grounding, Double.parseDouble(fields[1].trim()));
					List<Tuple> incoming = new ArrayList<Tuple>();
					Matcher matcher = atomPattern.matcher(fields[2].trim());
					while (matcher.find()) {
						incoming.add(new Tuple(matcher.group(), grounding));
					}
					incomingLinks.put(grounding, incoming);
					String ruleClass = fields[3].trim();
					String parameters = "";
					if (fields.length > 4)
						parameters = fields[4].trim();
					TalkingRule rule = null;
					try {
						Class c = Class.forName(ruleClass);
						// NOTE: We're not using the newer version
						// c.getDeclaredConstructor(String.class).newInstance(parameters)
						// since it was introduced after Java 8.
						rule = (TalkingRule) c.getConstructor(String.class).newInstance(parameters);
						talkingRules.put(rule.getName(), rule);
					} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
							| NullPointerException | IllegalArgumentException | InvocationTargetException
							 | NoSuchMethodException | SecurityException e) {
						System.err.println("Could not create TalkingRule of type " + ruleClass + ". Tried to call `new "
								+ ruleClass.substring(ruleClass.lastIndexOf('.')) + "(" + parameters + ")`:");
						e.printStackTrace();
						System.err.println("Skipping this entry.");
					}
					break;
				case ATOMS:
					String atom = fields[0].trim();
					atomNodes.add(atom);
					beliefValues.put(atom, Double.parseDouble(fields[1].trim()));
					atomStatus.put(atom, fields[2].trim());
					Tuple link = new Tuple(atom, fields[3].trim());
					links.add(link);
					linkStatus.put(link, fields[4].trim());
					if (fields[5].trim().equals("null")) {
						linkStrength.put(link, null);
					} else {
						linkStrength.put(link, Double.parseDouble(fields[5].trim()));
					}
					linkPressure.put(link, Boolean.parseBoolean(fields[6].trim()));
					if (!outgoingLinks.containsKey(atom)) {
						outgoingLinks.put(atom, new TreeSet<>());
					}
					outgoingLinks.get(atom).add(link);
					break;
				case SCORES:
					scoreMap.put(fields[0].trim(), Double.parseDouble(fields[1].trim()));
					break;
				case TALKING_PREDS:
					String predName = fields[0].trim();
					Integer arity = Integer.parseInt(fields[1].trim());
					String predClass = fields[2].trim();
					TalkingPredicate pred = null;
					try {
						Class c = Class.forName(predClass);
						// NOTE: We're not using the newer version
						// c.getDeclaredConstructor().newInstance();
						// since it was introduced after Java 8.
						pred = (TalkingPredicate) c.newInstance();
					} catch (Exception e) {
						pred = new TalkingPredicate(predName, arity);
					}
					talkingPreds.put(predName, pred);
					break;
				default:
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			System.err.println("Reached unexpected end of file while trying to deserialize InferenceResult instance.");
			e.printStackTrace();
		} catch (ArrayIndexOutOfBoundsException e) {
			System.err.println("Reached too-short TSV line while trying to deserialize InferenceResult instance:");
			System.err.println("\"" + line + "\"");
			e.printStackTrace();
		} finally {
			if (closeStream && (br != null)) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		RagFilter ragFilter = null;
		if (filterClass == null || filterClass.isEmpty()) {
			System.err.println("RagFilter not specified; creating standard RagFilter.");
			ragFilter = new RagFilter();
		} else {
			try {
				Class c = Class.forName(filterClass);
				// NOTE: We're not using the newer version
				// c.getDeclaredConstructor().newInstance();
				// since it was introduced after Java 8.
				ragFilter = (RagFilter) c.newInstance();
//			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException
//					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
			} catch (Exception e) {
				System.err.println("Could not create RagFilter of type " + filterClass + ":");
				e.printStackTrace();
				System.err.println("Creating standard RagFilter instead.");
				ragFilter = new RagFilter();
			}
		}
		ragFilter.setAll(beliefValues, groundPreds2ActualNames, ignoreList, ignoreInGui, preventUserInteraction);
		RuleAtomGraph rag = new RuleAtomGraph(groundingNodes, groundingStatus, equalityGroundings, atomNodes,
				atomStatus, links, linkStatus, linkPressure, linkStrength, outgoingLinks, incomingLinks, ragFilter);

		for (TalkingRule rule : talkingRules.values()) {
			rule.setTalkingPredicates(talkingPreds);
		}

		if (scoreMap.isEmpty())
			return new InferenceResult(rag);
		if (groundingNodes.isEmpty())
			return new InferenceResult(scoreMap);
		return new InferenceResult(rag, scoreMap);
	}

}

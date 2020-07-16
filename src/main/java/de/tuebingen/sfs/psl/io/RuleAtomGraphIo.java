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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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

import de.tuebingen.sfs.eie.talk.pred.EinhPred;
import de.tuebingen.sfs.psl.engine.PslProblem;
import de.tuebingen.sfs.psl.engine.RagFilter;
import de.tuebingen.sfs.psl.engine.RuleAtomGraph;
import de.tuebingen.sfs.psl.talk.TalkingPredicate;
import de.tuebingen.sfs.psl.talk.TalkingRule;
import de.tuebingen.sfs.psl.util.data.Tuple;

public class RuleAtomGraphIo {

	public static String RAG_PATH = "src/test/resources/serialization/rag.txt";

	private static enum TSV_SECTION {
		ATOMS, GROUNDINGS, TALKING_PREDS, TALKING_RULES
	}

	public static void saveToFile(RuleAtomGraph rag, PslProblem problem, ObjectMapper mapper) {
		saveToFile(rag, problem.getTalkingPredicates(), problem.getTalkingRules(), mapper);
	}

	public static void saveToFile(RuleAtomGraph rag, PslProblem problem, ObjectMapper mapper, File path) {
		saveToFile(rag, problem.getTalkingPredicates(), problem.getTalkingRules(), mapper, path);
	}

	public static void saveToFile(RuleAtomGraph rag, Map<String, TalkingPredicate> talkingPreds,
			Map<String, TalkingRule> talkingRules, ObjectMapper mapper) {
		saveToFile(rag, talkingPreds, talkingRules, mapper, new File(RAG_PATH));
	}

	public static void saveToFile(RuleAtomGraph rag, Map<String, TalkingPredicate> talkingPreds,
			Map<String, TalkingRule> talkingRules, ObjectMapper mapper, File path) {
		StringBuilder sb = new StringBuilder();
		RagFilter filter = rag.getRagFilter();
		sb.append("RAG FILTER\n===============");
		sb.append("\nCLASS\t").append(filter.getClass().getName());
		sb.append("\nIGNORE LIST\t").append(filter.getIgnoreList());
		sb.append("\nIGNORE IN GUI\t").append(filter.getIgnoreInGui());
		sb.append("\nPRED TO NAME\t").append(filter.getGroundPred2ActualNames());

		sb.append("\n\n\nRULE GROUNDINGS\n===============\nGROUNDING\tSTATUS\tINCOMING\n");
		for (String grounding : rag.getGroundingNodes()) {
			sb.append(grounding).append("\t").append(rag.getGroundingStatus(grounding)).append("\t");
			sb.append(rag.getIncomingLinks(grounding).stream().map(x -> x.get(0)).collect(Collectors.toList()))
					.append("\n");
		}
		sb.append("\n\nEQUALITY RULES\n===============\n").append(rag.getEqualityGroundings());
		sb.append("\n\n\nTALKING RULE CLASSES\n===============\n");
		for (Entry<String, TalkingRule> entry : talkingRules.entrySet()) {
			sb.append(entry.getKey()).append("\t").append(entry.getValue().getClass().getName()).append("\n");
		}
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
		sb.append("\n\nTALKING PREDICATE CLASSES\n===============\nNAME\tARITY\tCLASS\n");
		for (Entry<String, TalkingPredicate> entry : talkingPreds.entrySet()) {
			sb.append(entry.getKey()).append("\t").append(entry.getValue().getArity()).append("\t")
					.append(entry.getValue().getClass().getName()).append("\n");
		}

		try (OutputStream out = new FileOutputStream(path)) {
			out.write(sb.toString().getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static RuleAtomGraph ragFromFile(ObjectMapper mapper, Map<String, TalkingPredicate> talkingPreds,
			Map<String, TalkingRule> talkingRules) {
		return ragFromFile(mapper, RAG_PATH, talkingPreds, talkingRules);
	}

	public static RuleAtomGraph ragFromFile(ObjectMapper mapper, String path,
			Map<String, TalkingPredicate> talkingPreds, Map<String, TalkingRule> talkingRules) {
		try {
			return ragFromFile(mapper, new FileInputStream(path), talkingPreds, talkingRules);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	@SuppressWarnings("rawtypes")
	public static RuleAtomGraph ragFromFile(ObjectMapper mapper, InputStream is,
			Map<String, TalkingPredicate> talkingPreds, Map<String, TalkingRule> talkingRules) {
		if (talkingPreds == null)
			talkingPreds = new TreeMap<>();
		if (talkingRules == null)
			talkingRules = new TreeMap<>();

		Pattern atomPattern = Pattern.compile("\\w{4,}\\([^\\(]+\\)");

		String filterClass = null;
		Set<String> ignoreList = new TreeSet<>();
		Set<String> ignoreInGui = new TreeSet<>();
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

		String line = "";
		TSV_SECTION current = null;
		try (InputStreamReader isr = new InputStreamReader(is, "UTF-8"); BufferedReader br = new BufferedReader(isr)) {
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (line.equalsIgnoreCase("RAG FILTER")) {
					br.readLine(); // ====
					for (int i = 0; i < 4; i++) {
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
				if (line.equalsIgnoreCase("TALKING RULE CLASSES")) {
					br.readLine(); // ====
					current = TSV_SECTION.TALKING_RULES;
					continue;
				}
				if (line.equalsIgnoreCase("ATOM LINKS")) {
					br.readLine(); // ====
					br.readLine(); // table header
					current = TSV_SECTION.ATOMS;
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
				case TALKING_PREDS:
					String predName = fields[0].trim();
					Integer arity = Integer.parseInt(fields[1].trim());
					String predClass = fields[2].trim();
					TalkingPredicate pred = null;
					try {
						Class c = Class.forName(predClass);
						pred = (TalkingPredicate) c.newInstance();
					} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
							| NullPointerException | SecurityException | IllegalArgumentException e) {
						pred = new TalkingPredicate(predName, arity);
					}
					talkingPreds.put(predName, pred);
					break;
				case TALKING_RULES:
					String ruleName = fields[0].trim();
					String ruleClass = fields[1].trim();
					TalkingRule rule = null;
					try {
						Class c = Class.forName(ruleClass);
						rule = (TalkingRule) c.newInstance();
						talkingRules.put(ruleName, rule);
					} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
							| NullPointerException e) {
						System.err.println(
								"Could not create TalkingRule of type " + ruleClass + " for " + ruleName + ":");
						e.printStackTrace();
						System.err.println("Skipping this entry.");
					}
					break;
				default:
					break;
				}
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			System.err.println("Reached unexpected end of file while trying to deserialize RuleAtomGraph instance.");
			e.printStackTrace();
		} catch (ArrayIndexOutOfBoundsException e) {
			System.err.println("Reached too-short TSV line while trying to deserialize RuleAtomGraph instance:");
			System.err.println("\"" + line + "\"");
			e.printStackTrace();
		}

		RagFilter ragFilter = null;
		if (filterClass == null || filterClass.isEmpty()) {
			System.err.println("RagFilter not specified; creating standard RagFilter.");
			ragFilter = new RagFilter();
		} else {
			try {
				Class c = Class.forName(filterClass);
				ragFilter = (RagFilter) c.newInstance();
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
				System.err.println("Could not create RagFilter of type " + filterClass + ":");
				e.printStackTrace();
				System.err.println("Creating standard RagFilter instead.");
				ragFilter = new RagFilter();
			}
		}
		ragFilter.setAll(beliefValues, groundPreds2ActualNames, ignoreList, ignoreInGui);
		RuleAtomGraph rag = new RuleAtomGraph(groundingNodes, groundingStatus, equalityGroundings, atomNodes,
				atomStatus, links, linkStatus, linkPressure, linkStrength, outgoingLinks, incomingLinks, ragFilter);

		for (TalkingRule rule : talkingRules.values()) {
			rule.setTalkingPredicates(talkingPreds);
		}

		return rag;
	}

}

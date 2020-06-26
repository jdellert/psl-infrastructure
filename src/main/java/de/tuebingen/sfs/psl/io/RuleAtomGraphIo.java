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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tuebingen.sfs.psl.engine.RagFilter;
import de.tuebingen.sfs.psl.engine.RuleAtomGraph;
import de.tuebingen.sfs.psl.util.data.Tuple;

public class RuleAtomGraphIo {

	public static String RAG_PATH = "src/test/resources/serialization/rag.txt";
	public static String RAG_FILTER_PATH = "src/test/resources/serialization/filter.json";

	public static void saveToFile(RuleAtomGraph rag, ObjectMapper mapper) {
		saveToFile(rag, mapper, new File(RAG_PATH));
	}

	public static void saveToFile(RuleAtomGraph rag, ObjectMapper mapper, File path) {
		StringBuilder sb = new StringBuilder();
		RagFilter filter = rag.getRagFilter();
		sb.append("RAG FILTER\n===============");
//		sb.append("RAG FILTER\n===============\nPATH\t");
//		sb.append(RAG_FILTER_PATH);
//		saveToJson(filter, mapper, new File(RAG_FILTER_PATH));
		sb.append("\nCLASS\t").append(filter.getClass());
		sb.append("\nIGNORE LIST\t").append(filter.getIgnoreList());
		sb.append("\nIGNORE IN GUI\t").append(filter.getIgnoreInGui());
		sb.append("\nPRED TO NAME\t").append(filter.getGroundPred2ActualNames());
		
		sb.append("\n\n\nRULE GROUNDINGS\n===============\nGROUNDING\tSTATUS\tINCOMING\n");
		for (String grounding : rag.getGroundingNodes()) {
			sb.append(grounding).append("\t").append(rag.getGroundingStatus(grounding)).append("\t");
			sb.append("\t")
					.append(rag.getIncomingLinks(grounding).stream().map(x -> x.get(0)).collect(Collectors.toList()))
					.append("\n");
		}
		sb.append("\n\nEQUALITY RULES\n===============\n").append(rag.getEqualityGroundings());
		sb.append(
				"\n\n\nATOM LINKS\n===============\nATOM\tBELIEF VALUE\tATOM STATUS\tGROUNDING\tLINK STATUS\tLINK STRENGTH\tLINK PRESSURE\n");
		for (String atom : rag.getAtomNodes()) {
			for (Tuple link : rag.getOutgoingLinks(atom)) {
				sb.append(atom).append("\t").append(filter.getValueForAtom(atom)).append("\t").append(rag.getAtomStatus(atom)).append("\t");
				sb.append(link.get(1)).append("\t").append(rag.getLinkStatus(link)).append("\t");
				sb.append(rag.getLinkStrength(link)).append("\t").append(rag.getLinkPressure(link)).append("\n");
			}
		}

		try (OutputStream out = new FileOutputStream(path)) {
			out.write(sb.toString().getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static RuleAtomGraph ragFromFile(ObjectMapper mapper) {
		return ragFromFile(mapper, new File(RAG_PATH));
	}

	public static RuleAtomGraph ragFromFile(ObjectMapper mapper, File file) {
		try {
			return ragFromFile(mapper, new FileInputStream(file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static RuleAtomGraph ragFromFile(ObjectMapper mapper, InputStream is) {
//		String filterPath = null;
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
		boolean readingAtoms = false;
		boolean readingGroundings = false;
		try (InputStreamReader isr = new InputStreamReader(is, "UTF-8"); BufferedReader br = new BufferedReader(isr)) {
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (line.equalsIgnoreCase("RAG FILTER")) {
					br.readLine();
					for (int i = 0; i < 4; i++) {
						line = br.readLine().trim();
						if (line.startsWith("TYPE")) {
							filterClass = line.split("\t")[1];
//						} else if (line.startsWith("PATH")) {
//							filterPath = line.split("\t")[1];
						} else if (line.startsWith("IGNORE LIST")) {
							line = line.split("\t")[1].trim();
							line = line.substring(1, line.length() - 1); // Remove [ ]
							for (String item : line.split(",")){
								ignoreList.add(item.trim());
							}
						} else if (line.startsWith("IGNORE IN GUI")) {
							line = line.split("\t")[1];
							line = line.substring(1, line.length() - 1); // Remove [ ]
							for (String item : line.split(",")){
								ignoreInGui.add(item.trim());
							}
						} else if (line.startsWith("PRED")) {
							line = line.split("\t")[1];
							line = line.substring(1, line.length() - 1); // Remove { }
							for (String item : line.split(",")){
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
					readingGroundings = true;
					readingAtoms = false;
					continue;
				}
				if (line.equalsIgnoreCase("ATOM LINKS")) {
					br.readLine(); // ====
					br.readLine(); // table header
					readingGroundings = false;
					readingAtoms = true;
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
				if (readingGroundings) {
					String[] fields = line.split("\t");
					String grounding = fields[0].trim();
					groundingNodes.add(grounding);
					groundingStatus.put(grounding, Double.parseDouble(fields[1].trim()));
					List<Tuple> incoming = new ArrayList<Tuple>();
					for (String atom : fields[2].split(",")) {
						incoming.add(new Tuple(atom.trim(), grounding));
					}
					incomingLinks.put(grounding, incoming);
				}
				if (readingAtoms) {
					String[] fields = line.split("\t");
					String atom = fields[0].trim();
					atomNodes.add(atom);
					beliefValues.put(atom, Double.parseDouble(fields[1].trim()));
					atomStatus.put(atom, fields[2].trim());
					Tuple link = new Tuple(atom, fields[3].trim());
					links.add(link);
					linkStatus.put(link, fields[4].trim());
					if (fields[5].trim().equals("null")){
						linkStrength.put(link, null);
					} else {
						linkStrength.put(link, Double.parseDouble(fields[5].trim()));
					}
					linkPressure.put(link, Boolean.parseBoolean(fields[6].trim()));
					if (!outgoingLinks.containsKey(atom)) {
						outgoingLinks.put(atom, new TreeSet<>());
					}
					outgoingLinks.get(atom).add(link);
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
			System.err.println(line);
			e.printStackTrace();
		}

//		RagFilter renderer = ragFilterFromJson(mapper, new File(filterPath), filterClass);
		RagFilter ragFilter = null;
		if (filterClass == null || filterClass.isEmpty()) {
			ragFilter = new RagFilter();
		} else {
			try {
				Class c = Class.forName(filterClass);
				ragFilter = (RagFilter) c.newInstance();
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
				System.err.println("Could not create RagFilter of type " + filterClass);
				System.err.println("Creating normal RagFilter instead.");
				ragFilter = new RagFilter();
				e.printStackTrace();
			}
		}
		ragFilter.setAll(beliefValues, groundPreds2ActualNames, ignoreList, ignoreInGui);
		
		RuleAtomGraph rag = new RuleAtomGraph(groundingNodes, groundingStatus, equalityGroundings, atomNodes,
				atomStatus, links, linkStatus, linkPressure, linkStrength, outgoingLinks, incomingLinks, ragFilter);
		return rag;
	}

	public static void saveToJson(RagFilter renderer, ObjectMapper mapper) {
		saveToJson(renderer, mapper, new File(RAG_FILTER_PATH));
	}

	public static void saveToJson(RagFilter renderer, ObjectMapper mapper, File path) {
		try {
			ObjectNode rootNode = mapper.createObjectNode();
			rootNode.set("ignoreList",
					(ArrayNode) mapper.readTree(mapper.writeValueAsString(renderer.getIgnoreList())));
			rootNode.set("ignoreInGui",
					(ArrayNode) mapper.readTree(mapper.writeValueAsString(renderer.getIgnoreInGui())));
			rootNode.set("beliefValues",
					(ObjectNode) mapper.readTree(mapper.writeValueAsString(renderer.getBeliefValues())));
			rootNode.set("groundPred2ActualNames",
					(ObjectNode) mapper.readTree(mapper.writeValueAsString(renderer.getGroundPred2ActualNames())));
			mapper.writerWithDefaultPrettyPrinter().writeValue(path, rootNode);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static RagFilter ragFilterFromJson(ObjectMapper mapper) {
		return ragFilterFromJson(mapper, new File(RAG_FILTER_PATH), null);
	}

	public static RagFilter ragFilterFromJson(ObjectMapper mapper, String className) {
		return ragFilterFromJson(mapper, new File(RAG_FILTER_PATH), className);
	}

	public static RagFilter ragFilterFromJson(ObjectMapper mapper, File path) {
		return ragFilterFromJson(mapper, path, null);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static RagFilter ragFilterFromJson(ObjectMapper mapper, File path, String className) {
		Map<String, String> groundPred2ActualNames = null;
		Set<String> ignoreList = null;
		Set<String> ignoreInGui = null;
		Map<String, Double> transparencyMap = null;
		try {
			// TODO (vbl) check if JAR-compatible (stream)
			JsonNode rootNode = mapper.readTree(path);
			ignoreList = mapper.treeToValue(rootNode.path("ignoreList"), TreeSet.class);
			ignoreInGui = mapper.treeToValue(rootNode.path("ignoreInGui"), TreeSet.class);
			groundPred2ActualNames = mapper.treeToValue(rootNode.path("groundPred2ActualNames"), TreeMap.class);
			transparencyMap = mapper.treeToValue(rootNode.path("beliefValues"), TreeMap.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		RagFilter ragFilter = null;
		if (className == null || className.isEmpty()) {
			ragFilter = new RagFilter();
		} else {
			try {
				Class c = Class.forName(className);
				ragFilter = (RagFilter) c.newInstance();
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
				System.err.println("Could not create RagFilter of type " + className);
				System.err.println("Creating normal RagFilter instead.");
				ragFilter = new RagFilter();
				e.printStackTrace();
			}
		}

		ragFilter.setAll(transparencyMap, groundPred2ActualNames, ignoreList, ignoreInGui);
		return ragFilter;
	}

}

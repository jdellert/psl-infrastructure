package de.tuebingen.sfs.psl.engine;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.application.groundrulestore.MemoryGroundRuleStore;
import org.linqs.psl.application.util.Grounding;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.AbstractGroundArithmeticRule;
import org.linqs.psl.model.rule.logical.AbstractGroundLogicalRule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.function.FunctionComparator;

import de.tuebingen.sfs.psl.util.color.ColorUtils;
import de.tuebingen.sfs.psl.util.data.RankingEntry;
import de.tuebingen.sfs.psl.util.data.Tuple;
import de.tuebingen.sfs.util.InferenceLogger;

public class RuleAtomGraph {
	public static boolean GROUNDING_OUTPUT = false;
	public static boolean ATOM_VALUE_OUTPUT = false;
	public static boolean GROUNDING_SCORE_OUTPUT = false;
	
	Set<String> atomNodes;
	Map<String,String> atomStatus;
	
	Set<String> groundingNodes;
	Map<String,Double> groundingStatus;
	Set<String> equalityGroundings;
	
	Set<Tuple> links;
	Map<Tuple,String> linkStatus;
	Map<Tuple,Boolean> linkPressure;
	Map<Tuple,Double> linkStrength;
	Map<String,Set<Tuple>> outgoingLinks;
	Map<String,List<Tuple>> incomingLinks;
	
	RagFilter renderer;
	InferenceLogger logger;

	public RuleAtomGraph(RagFilter renderer) {
		this(renderer, new InferenceLogger());
	}

	public RuleAtomGraph(RagFilter renderer, InferenceLogger logger) {
		this.renderer = renderer;
		this.logger = logger;

		atomNodes = new TreeSet<String>();
		atomStatus = new TreeMap<String,String>();

		groundingNodes = new TreeSet<String>();
		groundingStatus = new TreeMap<String,Double>();
		equalityGroundings = new TreeSet<String>();

		links = new TreeSet<Tuple>();
		linkStatus = new TreeMap<Tuple, String>();
		linkPressure = new TreeMap<Tuple, Boolean>();
		linkStrength = new TreeMap<Tuple, Double>();
		outgoingLinks = new TreeMap<String,Set<Tuple>>();
		incomingLinks = new TreeMap<String,List<Tuple>>();
	}

	public RuleAtomGraph(PslProblem problem, RagFilter renderer) {
		this(problem, renderer, new InferenceLogger());
	}
	
	public RuleAtomGraph(PslProblem problem, RagFilter renderer, InferenceLogger logger) {
		this(renderer, logger);
		Reasoner reasoner = new ADMMReasoner();
		GroundRuleStore grs = new MemoryGroundRuleStore();
		// TODO check whether this works. The DB still needs to be opened/closed by calling dbManager.openDatabase(problemId, write, read) (vbl)
		PersistedAtomManager atomManager = new PersistedAtomManager(problem.getDbManager().getDatabase(problem.getName()));
//		PersistedAtomManager atomManager = new PersistedAtomManager(((AtomStorePSL) problem.atoms).db);
		Grounding.groundAll(problem.getPslModel(), atomManager, grs); // TODO: Difference reasoner/grs?

		for (Rule rule : problem.getPslModel().getRules()) {
			String ruleName = problem.getNameForRule(rule);
			if (GROUNDING_OUTPUT) logger.logln(ruleName + ": " + rule);

			int groundingCount = 1;
			for (GroundRule groundRule : grs.getGroundRules(rule)) {
				String groundingName = ruleName + "[" + (groundingCount++) + "]";
				add(groundingName, groundRule);
			}
		}
	}

	public RuleAtomGraph(PslProblem problem, RagFilter renderer, List<List<GroundRule>> grs) {
		this(problem, renderer, grs, new InferenceLogger());
	}
	
	public RuleAtomGraph(PslProblem problem, RagFilter renderer, List<List<GroundRule>> grs,
			InferenceLogger logger) {
		this(renderer, logger);

		List<Rule> rules = problem.listRules();
		if (rules.size() != grs.size())
			logger.logln("WARNING: You seem to have added/removed atoms since the last inference. "
					+ "Please create the RuleAtomGraph before adding/removing atoms or the graph will not represent "
					+ "the result of the last inference properly.");

		for (int r = 0; r < rules.size(); r++) {
			Rule rule = rules.get(r);
			String ruleName = problem.getNameForRule(rule);
			if (GROUNDING_OUTPUT)
				logger.logln(ruleName + ": " + rule);

			int groundingCount = 1;
			for (GroundRule groundRule : grs.get(r)) {
				String groundingName = ruleName + "[" + (groundingCount++) + "]";
				add(groundingName, groundRule);
			}
		}
	}

	private void add(String groundingName, GroundRule groundRule) {
		groundingNodes.add(groundingName);

		if (GROUNDING_OUTPUT) logger.logln("  " + groundingName + "\t" + groundRule.toString());

		List<GroundAtom> groundAtoms = new LinkedList<GroundAtom>();
		// set to -1.0 and 1.0 for logical rules, representing the polarity
		List<Double> coefficients = new LinkedList<Double>();

		if (groundRule instanceof AbstractGroundArithmeticRule) {
			AbstractGroundArithmeticRule arithmeticRule = (AbstractGroundArithmeticRule) groundRule;
			coefficients = AbstractGroundArithmeticRuleAccess.extractCoefficients(arithmeticRule);
			groundAtoms = AbstractGroundArithmeticRuleAccess.extractAtoms(arithmeticRule);
		} else if (groundRule instanceof AbstractGroundLogicalRule) {
			AbstractGroundLogicalRule logicalRule = (AbstractGroundLogicalRule) groundRule;
			coefficients = AbstractGroundLogicalRuleAccess.extractSigns(logicalRule);
			groundAtoms = AbstractGroundLogicalRuleAccess.extractAtoms(logicalRule);
		}

		double[] values = extractValueVector(groundAtoms, coefficients, renderer);

		double bodyScore = computeBodyScore(coefficients, values);
		double headScore = computeHeadScore(coefficients, values);
		double distanceToSatisfaction = bodyScore - headScore;
		if (GROUNDING_SCORE_OUTPUT && groundRule instanceof AbstractGroundLogicalRule) {
			logger.logln("     headScore: " + headScore);
			logger.logln("     bodyScore: " + bodyScore);
			logger.logln("   distance to satisfaction: " + distanceToSatisfaction);
		}
		if (groundRule instanceof AbstractGroundLogicalRule) {
			groundingStatus.put(groundingName, distanceToSatisfaction);
		}

		double arithLHS = computeWeightedSum(coefficients, values);
		double arithRHS = 0.0;
		double arithRuleViolation = 0.0;

		if (groundRule instanceof AbstractGroundArithmeticRule) {
			AbstractGroundArithmeticRule arithRule = (AbstractGroundArithmeticRule) groundRule;
			FunctionComparator comparator = AbstractGroundArithmeticRuleAccess.extractComparator(arithRule);
			arithRHS = AbstractGroundArithmeticRuleAccess.extractConstant(arithRule);
			switch (comparator) {
				case SmallerThan:
					arithRuleViolation = arithLHS - arithRHS;
					break;
				case LargerThan:
					arithRuleViolation = arithRHS - arithLHS;
					break;
				case Equality:
					arithRuleViolation = Math.abs(arithLHS - arithRHS);
					equalityGroundings.add(groundingName);
					break;
				default:
					break;
			}
			if (GROUNDING_SCORE_OUTPUT) {
				logger.logln("     LHS: " + arithLHS);
				logger.logln("     RHS: " + arithRHS);
				logger.logln("  " + groundingName + ":\t" + arithRuleViolation);
			}
			groundingStatus.put(groundingName, arithRuleViolation);
		}

		for (int i = 0; i < groundAtoms.size(); i++) {
			GroundAtom atom = groundAtoms.get(i);
			if (!renderer.isRendered(atom.getPredicate().getName()))
				continue;
			String atomName = renderer.atomToSimplifiedString(atom);
			atomNodes.add(atomName);

			Tuple link = addLink(atomName, groundingName);

			double coeff = coefficients.get(i);

			if (groundRule instanceof AbstractGroundLogicalRule) {
				//positive literal in logical rule? up -> more satisfied, down -> less satisfied => green, +~
				if (coeff < 0) {
					linkStatus.put(link, "+");

					// Positive literal under pressure if it wants to go down
					double origValue = values[i];
					values[i] = Math.max(0.0, origValue - 0.1);
					double changedHeadScore = computeHeadScore(coefficients, values);
					values[i] = origValue;
					double headScoreDecrease = headScore - changedHeadScore;
					double effectiveRuleStrength = Math.max(headScoreDecrease, distanceToSatisfaction + headScoreDecrease);
					linkPressure.put(link, effectiveRuleStrength > 0.0);
				}
				//negative literal in logical rule? up -> less satisfied, down -> more satisfied => red, -~
				else {
					linkStatus.put(link, "-");

					// Negative literal under pressure if it wants to go up
					double origValue = values[i];
					values[i] = Math.min(1.0, origValue + 0.1);
					double changedBodyScore = computeBodyScore(coefficients, values);
					values[i] = origValue;
					double bodyScoreIncrease = changedBodyScore - bodyScore;
					double effectiveRuleStrength = Math.max(bodyScoreIncrease, distanceToSatisfaction + bodyScoreIncrease);
					linkPressure.put(link, effectiveRuleStrength > 0.0);
				}
			}
			else if (groundRule instanceof AbstractGroundArithmeticRule) {
				AbstractGroundArithmeticRule arithRule = (AbstractGroundArithmeticRule) groundRule;
				FunctionComparator comparator = AbstractGroundArithmeticRuleAccess.extractComparator(arithRule);
				double signum = Math.signum(coeff);
				//in inequality with polarity towards satisfaction (- in <=, + in >=)? => green +~
				if (signum == -1 && comparator == FunctionComparator.SmallerThan
						|| signum == 1 && comparator == FunctionComparator.LargerThan) {
					linkStatus.put(link, "+");

					double origValue = values[i];
					values[i] = Math.max(0.0, origValue - 0.1);
					double changedLHS = computeWeightedSum(coefficients, values);
					values[i] = origValue;
					double ruleViolationChange = (arithRHS - changedLHS) - arithRuleViolation;
					linkPressure.put(link, ruleViolationChange > 0.0);
				}
				//in inequality with polarity away from satisfaction (+ in <=, - in >=)? => red, -~
				else if (signum == 1 && comparator == FunctionComparator.SmallerThan
						|| signum == -1 && comparator == FunctionComparator.LargerThan) {
					linkStatus.put(link, "-");

					double origValue = values[i];
					values[i] = Math.min(1.0, origValue + 0.1);
					double changedLHS = computeWeightedSum(coefficients, values);
					values[i] = origValue;
					double ruleViolationChange = (arithRHS - changedLHS) - arithRuleViolation;
					linkPressure.put(link, ruleViolationChange > 0.0);
				}
				//in equation? would depend on current state! grey for positive coefficient (LHS), brown for negative (RHS)
				else if (comparator == FunctionComparator.Equality) {
					linkStatus.put(link, "=");
				}
			}
		}
	}

	public double getValue(String atomString) {
		return renderer.getValueForAtom(atomString);
	}

	public double updateValue(String atomString, double newValue) {
		return 1.0 - renderer.updateToneForAtom(atomString, newValue);
	}
	
	public Tuple addLink(String atomName, String groundingName) {
		Tuple link = new Tuple(atomName, groundingName);
		Set<Tuple> outgoingLinksForAtom = outgoingLinks.get(atomName);
		if (outgoingLinksForAtom == null) {
			outgoingLinksForAtom = new TreeSet<Tuple>();
			outgoingLinks.put(atomName, outgoingLinksForAtom);
		}
		outgoingLinksForAtom.add(link);
		List<Tuple> incomingLinksForGrounding = incomingLinks.get(groundingName);
		if (incomingLinksForGrounding == null) {
			incomingLinksForGrounding = new ArrayList<Tuple>();
			incomingLinks.put(groundingName, incomingLinksForGrounding);
		}
		incomingLinksForGrounding.add(link);
		return link;
	}

	public Set<String> getIgnoredPredicates() {
		return renderer.getIgnoreList();
	}
	
	public boolean renderAtomInGui(String atomName) {
		System.out.println(atomName);
		return renderer.isRenderedInGui(atomName.split("\\(")[0]);
	}
	
	public Set<Tuple> getOutgoingLinks(String atomName) {
		Set<Tuple> outgoingLinksForAtom = outgoingLinks.get(atomName);
		if (outgoingLinksForAtom == null) {
			outgoingLinksForAtom = new TreeSet<Tuple>();
		}
		return outgoingLinksForAtom;
	}

	public  Map<String,String> getLinkedAtomsForGroundingWithLinkStatus(String groundingName) {
		Map<String,String> atomsToStatus = new TreeMap<String,String>();
		List<Tuple> incomingLinksForGrounding = incomingLinks.get(groundingName);
		if (incomingLinksForGrounding == null) {
			incomingLinksForGrounding = new ArrayList<Tuple>();
		}
		for (Tuple link : incomingLinksForGrounding) {
			String atom = link.get(0);
			String status = linkStatus.get(link);
			atomsToStatus.put(atom, status);
		}
		return atomsToStatus;
	}

	public  List<Tuple> getLinkedAtomsForGroundingWithLinkStatusAsList(String groundingName) {
		List<Tuple> atomsToStatus = new ArrayList<>();
		List<Tuple> incomingLinksForGrounding = incomingLinks.get(groundingName);
		if (incomingLinksForGrounding == null) {
			incomingLinksForGrounding = new ArrayList<Tuple>();
		}
		for (Tuple link : incomingLinksForGrounding) {
			String atom = link.get(0);
			String status = linkStatus.get(link);
			atomsToStatus.add(new Tuple(atom, status));
		}
		return atomsToStatus;
	}

	public boolean putsPressureOnGrounding(String atomName, String groundingName) {
		return linkPressure.getOrDefault(new Tuple(atomName, groundingName), false);
	}

	public boolean isEqualityRule(String groundingName) {
		return equalityGroundings.contains(groundingName);
	}
	
	private double[] extractValueVector(List<GroundAtom> groundAtoms, List<Double> coefficients, RagFilter renderer) {
		double[] valueVector = new double[groundAtoms.size()];
		for (int i = 0; i < groundAtoms.size(); i++) {
			GroundAtom atom = groundAtoms.get(i);
			String atomRepresentation = renderer.atomToSimplifiedString(atom);
			double value = 1 - renderer.getToneForAtom(atomRepresentation);
			if (ATOM_VALUE_OUTPUT) logger.logln("      " + atomRepresentation + ": " + value + " (coeff = " + coefficients.get(i) + ")");
			valueVector[i] = value;
		}
		return valueVector;
	}
	
	private double computeBodyScore(List<Double> coefficients, double[] values) {
		double bodyScore = 1.0;
		for (int i = 0; i < coefficients.size(); i++) {
			if (coefficients.get(i) > 0) {
				bodyScore += values[i] - 1;
				if (bodyScore < 0.0) bodyScore = 0.0;
			}
		}
		return bodyScore;
	}
	
	private double computeHeadScore(List<Double> coefficients, double[] values) {
		double headScore = 0.0;
		for (int i = 0; i < coefficients.size(); i++) {
			if (coefficients.get(i) < 0) {
				headScore += values[i];	
				if (headScore > 1.0) headScore = 1.0;
			}
		}
		return headScore;
	}
	
	private double computeWeightedSum(List<Double> coefficients, double[] values) {
		double weightedSum = 0.0;
		for (int i = 0; i < coefficients.size(); i++) {
			weightedSum += coefficients.get(i) * Math.abs(values[i]);
		}
		return weightedSum;
	}
	
	public void printToStream(PrintStream out) {
		for (String atom : atomNodes) {
			for (Tuple link : getOutgoingLinks(atom)) {
				out.print(atom + " " + linkStatus.get(link) + "~ " + link.get(1) + "\n");
			}
		}
	}
	
	public String statusToColor(String status) {
		if (status.equals("+")) {
			return "#339966";
		} 
		else if (status.equals("-")) {
			return "#dd0000";
		}
		return "#c0c0c0";
	}
	
	public String distToSatisfactionToColor(double dist) {
		//System.err.print("distToSatisfactionToColor(" + dist + ") = ");
		double val = ((dist > 1) ? 1.0 : dist) * 50 + 50;
		double hue = Math.floor((100.0 - val) * 120 / 100);  // go from green to red
		return ColorUtils.hsvToRgb(hue, 1.0, 1.0);
	}
	
	public void exportToGraphML(PrintStream out) {
		printHeader(out);
		printNodes(out);
		printEdges(out);
		printClosingTags(out);
	}
	
	public void printHeader(PrintStream out) {
		out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		out.println("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"");
		out.println("    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
		out.println("    xmlns:y=\"http://www.yworks.com/xml/graphml\"");
		out.println("    xmlns:yed=\"http://www.yworks.com/xml/yed/3\"");
		out.println("    xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns");
		out.println("     http://www.yworks.com/xml/schema/graphml/1.1/ygraphml.xsd\">");
		out.println("  <key for=\"node\" id=\"d6\" yfiles.type=\"nodegraphics\"/>");
		out.println("  <key for=\"edge\" id=\"d9\" yfiles.type=\"edgegraphics\"/>");
		out.println("  <graph id=\"G\" edgedefault=\"undirected\">");
	}

	public void printNodes(PrintStream out) {
		for (String atomName : atomNodes) {
			out.println("    <node id=\"" + atomName + "\">");
			out.println("      <data key=\"d6\">");
			out.println("        <y:ShapeNode>");
			out.println("          <y:Geometry height=\"25\" width=\"150\"/>");
			out.println("          <y:Fill color=\"" + renderer.atomToColor(atomName) + "\" transparent=\"false\"/>");
			out.println("          <y:NodeLabel alignment=\"center\" fontsize=\"15\" textColor=\"#000000\" visible=\"true\">" + atomName + "</y:NodeLabel>");
			out.println("          <y:Shape type=\"roundrectangle\"/>");
			out.println("        </y:ShapeNode>");
			out.println("      </data>");
			out.println("    </node>");
		}
		for (String groundingName : groundingNodes) {
			out.println("    <node id=\"" + groundingName + "\">");
			out.println("      <data key=\"d6\">");
			out.println("        <y:ShapeNode>");
			out.println("          <y:Geometry height=\"25\" width=\"150\"/>");
			out.println("          <y:Fill color=\"#000000\" transparent=\"false\"/>");
			out.println("          <y:NodeLabel alignment=\"center\" fontsize=\"15\" textColor=\"" + distToSatisfactionToColor(groundingStatus.get(groundingName)) + "\" visible=\"true\">" + groundingName + "</y:NodeLabel>");
			out.println("          <y:Shape type=\"rectangle\"/>");
			out.println("        </y:ShapeNode>");
			out.println("      </data>");
			out.println("    </node>");
		}
	}

	public void printEdges(PrintStream out) {
		int edgeID = 0;
		for (String atom : atomNodes) {
			for (Tuple link : getOutgoingLinks(atom)) {
				String grounding = link.get(1);
				String targetArrow = "none";
				String status = linkStatus.get(link);
				String colorString = statusToColor(status);
				double width = 2.0;
				
				out.println("    <edge id=\"" + (edgeID++) + "\" source=\"" + atom + "\" target=\"" + grounding + "\">");
				out.println("      <data key=\"d9\">");
				out.println("        <y:PolyLineEdge>");
				out.println("          <y:LineStyle color=\"" + colorString + "\" type=\"line\" width=\"" + width + "\"/>");
				out.println("          <y:Arrows source=\"none\" target=\"" + targetArrow + "\"/>");
				out.println("        </y:PolyLineEdge>");
				out.println("      </data>");
				out.println("    </edge>");
			}
		}
	}

	public void printClosingTags(PrintStream out) {
		out.println("  </graph>");
		out.println("</graphml>");
	}

	public List<RankingEntry<String>> rankGroundingsByPressure(double minPressure) {
		List<RankingEntry<String>> groundingRanking = new ArrayList<RankingEntry<String>>(groundingNodes.size());
		for (String groundingName : groundingNodes) {
			double pressure = groundingStatus.get(groundingName);
			if (pressure > minPressure) {
				groundingRanking.add(new RankingEntry<String>(groundingName, pressure));
			}
		}
		Collections.sort(groundingRanking);
		return groundingRanking;
	}
}

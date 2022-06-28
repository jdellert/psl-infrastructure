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
package de.tuebingen.sfs.psl.engine;

import de.tuebingen.sfs.psl.util.color.ColorUtils;
import de.tuebingen.sfs.psl.util.color.HslColor;
import de.tuebingen.sfs.psl.util.data.RankingEntry;
import de.tuebingen.sfs.psl.util.data.Tuple;
import de.tuebingen.sfs.psl.util.log.InferenceLogger;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.grounding.MemoryGroundRuleStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.AbstractGroundArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.WeightedGroundArithmeticRule;
import org.linqs.psl.model.rule.logical.AbstractGroundLogicalRule;
import org.linqs.psl.model.rule.logical.WeightedGroundLogicalRule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.function.FunctionComparator;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class RuleAtomGraph {
	public static boolean GROUNDING_OUTPUT = false;
	public static boolean ATOM_VALUE_OUTPUT = false;
	public static boolean GROUNDING_SCORE_OUTPUT = false;
	public static final double COUNTERFACTUAL_OFFSET = 0.02;
	public static final double DISSATISFACTION_PRECISION = 0.01;

	// rule groundings
	Set<String> groundingNodes;
	Map<String, Double> groundingStatus;
	Set<String> equalityGroundings;
	Map<String, GroundRule> groundingToGroundRule;

	// atoms
	Set<String> atomNodes;
	Map<String, String> atomStatus;

	// atom + rule grounding combinations
	Set<Tuple> links;
	Map<Tuple, String> linkStatus;
	Map<Tuple, Double> linkToCounterfactual;
	Map<Tuple, double[]> equalityRuleLinkToCounterfactual;
	Map<Tuple, Double> linkStrength;
	Map<String, Set<Tuple>> outgoingLinks;
	Map<String, List<Tuple>> incomingLinks;

	RagFilter filter;
	InferenceLogger logger;

	public RuleAtomGraph(RagFilter renderer) {
		this(renderer, new InferenceLogger());
	}

	public RuleAtomGraph(RagFilter renderer, InferenceLogger logger) {
		this.filter = renderer;
		this.logger = logger;

		atomNodes = new TreeSet<String>();
		atomStatus = new TreeMap<String, String>();

		groundingNodes = new TreeSet<String>();
		groundingStatus = new TreeMap<String, Double>();
		equalityGroundings = new TreeSet<String>();
		groundingToGroundRule = new TreeMap<String, GroundRule>();

		links = new TreeSet<Tuple>();
		linkStatus = new TreeMap<Tuple, String>();
		linkToCounterfactual = new TreeMap<Tuple, Double>();
		equalityRuleLinkToCounterfactual = new TreeMap<>();
		linkStrength = new TreeMap<Tuple, Double>();
		outgoingLinks = new TreeMap<String, Set<Tuple>>();
		incomingLinks = new TreeMap<String, List<Tuple>>();
	}

	public RuleAtomGraph(Set<String> groundingNodes, Map<String, Double> groundingStatus,
						 Set<String> equalityGroundings, Set<String> atomNodes, Map<String, String> atomStatus, Set<Tuple> links,
						 Map<Tuple, String> linkStatus, Map<Tuple, Double> linkToCounterfactual, Map<Tuple, double[]> equalityRuleLinkToCounterfactual,
						 Map<Tuple, Double> linkStrength,
						 Map<String, Set<Tuple>> outgoingLinks, Map<String, List<Tuple>> incomingLinks, RagFilter renderer) {
		this.groundingNodes = groundingNodes;
		this.groundingStatus = groundingStatus;
		this.equalityGroundings = equalityGroundings;
		this.atomNodes = atomNodes;
		this.atomStatus = atomStatus;
		this.links = links;
		this.linkStatus = linkStatus;
		this.linkToCounterfactual = linkToCounterfactual;
		this.equalityRuleLinkToCounterfactual = equalityRuleLinkToCounterfactual;
		this.linkStrength = linkStrength;
		this.outgoingLinks = outgoingLinks;
		this.incomingLinks = incomingLinks;
		this.filter = renderer;
	}

	public RuleAtomGraph(PslProblem problem, RagFilter renderer) {
		this(problem, renderer, new InferenceLogger());
	}

	public RuleAtomGraph(PslProblem problem, RagFilter renderer, InferenceLogger logger) {
		this(renderer, logger);
		Reasoner reasoner = new ADMMReasoner();
		GroundRuleStore grs = new MemoryGroundRuleStore();
		// TODO check whether this works. The DB still needs to be opened/closed by
		// calling dbManager.openDatabase(problemId, write, read)
		PersistedAtomManager atomManager = new PersistedAtomManager(
				problem.getDbManager().getDatabase(problem.getName()));
//		PersistedAtomManager atomManager = new PersistedAtomManager(((AtomStorePSL) problem.atoms).db);
		Grounding.groundAll(problem.getPslModel(), atomManager, grs); // TODO: Difference reasoner/grs?

		for (Rule rule : problem.getPslModel().getRules()) {
			String ruleName = problem.getNameForRule(rule);
			if (GROUNDING_OUTPUT)
				logger.logln(ruleName + ": " + rule);

			int groundingCount = 1;
			for (GroundRule groundRule : grs.getGroundRules(rule)) {
				String groundingName = ruleName + "[" + (groundingCount++) + "]";
				if (groundRule instanceof AbstractGroundArithmeticRule) {
					addGroundArithmeticRule(groundingName, (AbstractGroundArithmeticRule) groundRule);
				} else {
					addGroundLogicalRule(groundingName, (AbstractGroundLogicalRule) groundRule);
				}
			}
		}
	}

	public RuleAtomGraph(PslProblem problem, RagFilter renderer, List<List<GroundRule>> grs) {
		this(problem, renderer, grs, new InferenceLogger());
	}

	public RuleAtomGraph(PslProblem problem, RagFilter renderer, List<List<GroundRule>> grs, InferenceLogger logger) {
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
				if (groundRule instanceof AbstractGroundArithmeticRule) {
					addGroundArithmeticRule(groundingName, (AbstractGroundArithmeticRule) groundRule);
				} else {
					addGroundLogicalRule(groundingName, (AbstractGroundLogicalRule) groundRule);
				}
			}
		}
	}

	public RagFilter getRagFilter() {
		return filter;
	}

	private void addGroundArithmeticRule(String groundingName, AbstractGroundArithmeticRule arithmeticRule) {
		groundingNodes.add(groundingName);
		groundingToGroundRule.put(groundingName, arithmeticRule);
		if (GROUNDING_OUTPUT)
			logger.logln("  " + groundingName + "\t" + arithmeticRule.toString());

		double weight = 1.0;
		if (arithmeticRule instanceof WeightedGroundArithmeticRule) {
			weight = ((WeightedGroundArithmeticRule) arithmeticRule).getWeight();
		}

		List<GroundAtom> groundAtoms = AbstractGroundArithmeticRuleAccess.extractAtoms(arithmeticRule);
		// set to -1.0 and 1.0 for logical rules, representing the polarity
		List<Double> coefficients = AbstractGroundArithmeticRuleAccess.extractCoefficients(arithmeticRule);
		double[] values = extractValueVector(groundAtoms, coefficients, filter);

		double arithLHS = computeWeightedSum(coefficients, values);
		double arithRHS = AbstractGroundArithmeticRuleAccess.extractConstant(arithmeticRule);
		double arithRuleViolation = 0.0;

		FunctionComparator comparator = AbstractGroundArithmeticRuleAccess.extractComparator(arithmeticRule);
		switch (comparator) {
			case LTE:
				arithRuleViolation = arithLHS - arithRHS;
				break;
			case GTE:
				arithRuleViolation = arithRHS - arithLHS;
				break;
			case EQ:
				arithRuleViolation = Math.abs(arithLHS - arithRHS);
				equalityGroundings.add(groundingName);
				break;
			default:
				break;
		}
		double weightedViolation = weight * arithRuleViolation;
		if (GROUNDING_SCORE_OUTPUT) {
			logger.logln("     LHS: " + arithLHS);
			logger.logln("     RHS: " + arithRHS);
			logger.logln("   rule violation: " + weight + " * " + arithRuleViolation + " = " + arithRuleViolation);
		}
		groundingStatus.put(groundingName, weightedViolation);

		for (int i = 0; i < groundAtoms.size(); i++) {
			GroundAtom atom = groundAtoms.get(i);
			if (!filter.isRendered(atom.getPredicate().getName()))
				continue;
			String atomName = filter.atomToSimplifiedString(atom);
			atomNodes.add(atomName);

			Tuple link = addLink(atomName, groundingName);

			double coeff = coefficients.get(i);
			double signum = Math.signum(coeff);
			//in inequality with polarity towards satisfaction (- in <=, + in >=)? => green +~
			if (signum == -1 && comparator == FunctionComparator.LTE
					|| signum == 1 && comparator == FunctionComparator.GTE) {
				linkStatus.put(link, "+");

				double origValue = values[i];
				values[i] = Math.max(0.0, origValue - COUNTERFACTUAL_OFFSET);
				double changedLHS = computeWeightedSum(coefficients, values);
				values[i] = origValue;
				double counterfactualViolation = weight * (arithRHS - changedLHS);
				linkToCounterfactual.put(link, counterfactualViolation);
				if (GROUNDING_SCORE_OUTPUT) {
					logger.logln("     counterfactual LHS: " + arithLHS);
					logger.logln("     RHS: " + arithRHS);
					logger.logln("   counterfactual rule violation: " + weight + " * " + (arithRHS - changedLHS) + " = " + counterfactualViolation);
				}
			}
			//in inequality with polarity away from satisfaction (+ in <=, - in >=)? => red, -~
			else if (signum == 1 && comparator == FunctionComparator.LTE
					|| signum == -1 && comparator == FunctionComparator.GTE) {
				linkStatus.put(link, "-");

				double origValue = values[i];
				values[i] = Math.min(1.0, origValue + COUNTERFACTUAL_OFFSET);
				double changedLHS = computeWeightedSum(coefficients, values);
				values[i] = origValue;
				double counterfactualViolation = weight * (arithRHS - changedLHS);
				linkToCounterfactual.put(link, counterfactualViolation);
				if (GROUNDING_SCORE_OUTPUT) {
					logger.logln("     counterfactual LHS: " + arithLHS);
					logger.logln("     RHS: " + arithRHS);
					logger.logln("   counterfactual rule violation: " + weight + " * " + (arithRHS - changedLHS) + " = " + counterfactualViolation);
				}
			}
			//in equation? would depend on current state! grey for positive coefficient (LHS), brown for negative (RHS)
			else if (comparator == FunctionComparator.EQ) {
				linkStatus.put(link, "=");

				double origValue = values[i];
				values[i] = Math.max(0.0, origValue - COUNTERFACTUAL_OFFSET);
				double changedLHS = computeWeightedSum(coefficients, values);
				double counterfactualLowerViolation = weight * Math.abs(arithRHS - changedLHS);
				if (GROUNDING_SCORE_OUTPUT) {
					logger.logln("     counterfactual (lower) LHS: " + changedLHS);
					logger.logln("     RHS: " + arithRHS);
					logger.logln("   counterfactual (lower) rule violation: " + weight + " * " + (arithRHS - changedLHS) + " = " + counterfactualLowerViolation);
				}
				values[i] = Math.min(1.0, origValue + COUNTERFACTUAL_OFFSET);
				changedLHS = computeWeightedSum(coefficients, values);
				values[i] = origValue;
				double counterfactualHigherViolation = weight * Math.abs(arithRHS - changedLHS);
				if (GROUNDING_SCORE_OUTPUT) {
					logger.logln("     counterfactual (higher) LHS: " + changedLHS);
					logger.logln("     RHS: " + arithRHS);
					logger.logln("   counterfactual (lower) rule violation: " + weight + " * " + (arithRHS - changedLHS) + " = " + counterfactualHigherViolation);
				}
				equalityRuleLinkToCounterfactual.put(link, new double[] {counterfactualLowerViolation, counterfactualHigherViolation});
			}
		}
	}

	private void addGroundLogicalRule(String groundingName, AbstractGroundLogicalRule logicalRule) {
		groundingNodes.add(groundingName);
		groundingToGroundRule.put(groundingName, logicalRule);
		if (GROUNDING_OUTPUT)
			logger.logln("  " + groundingName + "\t" + logicalRule.toString());

		double weight = 1.0;
		if (logicalRule instanceof WeightedGroundLogicalRule) {
			weight = ((WeightedGroundLogicalRule) logicalRule).getWeight();
		}

		List<GroundAtom> groundAtoms = AbstractGroundLogicalRuleAccess.extractAtoms(logicalRule);
		// set to -1.0 and 1.0 for logical rules, representing the polarity
		List<Double> coefficients = AbstractGroundLogicalRuleAccess.extractSigns(logicalRule);
		double[] values = extractValueVector(groundAtoms, coefficients, filter);

		double bodyScore = computeBodyScore(coefficients, values);
		double headScore = computeHeadScore(coefficients, values);
		double distanceToSatisfaction = bodyScore - headScore;
		double weightedDistToSat = weight * distanceToSatisfaction;
		if (GROUNDING_SCORE_OUTPUT) {
			logger.logln("     headScore: " + headScore);
			logger.logln("     bodyScore: " + bodyScore);
			logger.logln("   distance to satisfaction: " + weight + " * " + distanceToSatisfaction + " = " + weightedDistToSat);
		}
		groundingStatus.put(groundingName, weightedDistToSat);

		for (int i = 0; i < groundAtoms.size(); i++) {
			GroundAtom atom = groundAtoms.get(i);
			if (!filter.isRendered(atom.getPredicate().getName()))
				continue;
			String atomName = filter.atomToSimplifiedString(atom);
			atomNodes.add(atomName);
			Tuple link = addLink(atomName, groundingName);

			double coeff = coefficients.get(i);
			// positive literal in logical rule? up -> more satisfied, down -> less
			// satisfied => green, +~
			if (coeff < 0) {
				linkStatus.put(link, "+");

				// Positive literal under pressure if it wants to go down
				double origValue = values[i];
				values[i] = Math.max(0.0, origValue - COUNTERFACTUAL_OFFSET);
				double changedHeadScore = computeHeadScore(coefficients, values);
				values[i] = origValue;
				double counterfactualDistance = weight * (bodyScore - changedHeadScore);
				linkToCounterfactual.put(link, counterfactualDistance);
				if (GROUNDING_SCORE_OUTPUT) {
					logger.logln("     counterfactual headScore: " + changedHeadScore);
					logger.logln("     bodyScore: " + bodyScore);
					logger.logln("   counterfactual distance to satisfaction: " + weight + " * " + (bodyScore - changedHeadScore) + " = " + counterfactualDistance);
				}
			}
			// negative literal in logical rule? up -> less satisfied, down -> more
			// satisfied => red, -~
			else {
				linkStatus.put(link, "-");

				// Negative literal under pressure if it wants to go up
				double origValue = values[i];
				values[i] = Math.min(1.0, origValue + COUNTERFACTUAL_OFFSET);
				double changedBodyScore = computeBodyScore(coefficients, values);
				values[i] = origValue;
				double counterfactualDistance = weight * (changedBodyScore - headScore);
				linkToCounterfactual.put(link, counterfactualDistance);
				if (GROUNDING_SCORE_OUTPUT) {
					logger.logln("     headScore: " + headScore);
					logger.logln("     counterfactual bodyScore: " + changedBodyScore);
					logger.logln("   counterfactual distance to satisfaction: " + weight + " * " + (changedBodyScore - headScore) + " = " + counterfactualDistance);
				}
			}
		}
	}

	public double getValue(String atomString) {
		return filter.getValueForAtom(atomString);
	}

	public double updateValue(String atomString, double newValue) {
		return 1.0 - filter.updateToneForAtom(atomString, newValue);
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
		return filter.getIgnoreList();
	}

	public boolean renderAtomInGui(String atomName) {
		return filter.isRenderedInGui(atomName.split("\\(")[0]);
	}

	public boolean preventUserInteraction(String pred) {
		return filter.getPreventUserInteraction().contains(pred);
	}

	public Set<Tuple> getOutgoingLinks(String atomName) {
		Set<Tuple> outgoingLinksForAtom = outgoingLinks.get(atomName);
		if (outgoingLinksForAtom == null) {
			outgoingLinksForAtom = new TreeSet<Tuple>();
		}
		return outgoingLinksForAtom;
	}

	public GroundRule getRuleForGrounding(String groundingName) {
		return groundingToGroundRule.get(groundingName);
	}

	public List<Tuple> getLinkedAtomsForGroundingWithLinkStatusAsList(String groundingName) {
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

	public double distanceToSatisfaction(String groundingName) {
		double dist = groundingStatus.get(groundingName);
		if (dist < DISSATISFACTION_PRECISION) {
			return 0;
		}
		return dist;
	}

	public boolean isEqualityRule(String groundingName) {
		return equalityGroundings.contains(groundingName);
	}

	private double[] extractValueVector(List<GroundAtom> groundAtoms, List<Double> coefficients, RagFilter renderer) {
		double[] valueVector = new double[groundAtoms.size()];
		for (int i = 0; i < groundAtoms.size(); i++) {
			GroundAtom atom = groundAtoms.get(i);
			String atomRepresentation = renderer.atomToSimplifiedString(atom);
			double value = renderer.getValueForAtom(atomRepresentation);
			if (ATOM_VALUE_OUTPUT)
				logger.logln("      " + atomRepresentation + ": " + value + " (coeff = " + coefficients.get(i) + ")");
			valueVector[i] = value;
		}
		return valueVector;
	}

	private double computeBodyScore(List<Double> coefficients, double[] values) {
		double bodyScore = 1.0;
		for (int i = 0; i < coefficients.size(); i++) {
			if (coefficients.get(i) > 0) {
				bodyScore += values[i] - 1;
				if (bodyScore < 0.0)
					bodyScore = 0.0;
			}
		}
		return bodyScore;
	}

	private double computeHeadScore(List<Double> coefficients, double[] values) {
		double headScore = 0.0;
		for (int i = 0; i < coefficients.size(); i++) {
			if (coefficients.get(i) < 0) {
				headScore += values[i];
				if (headScore > 1.0)
					headScore = 1.0;
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
		} else if (status.equals("-")) {
			return "#dd0000";
		}
		return "#c0c0c0";
	}

	public String distToSatisfactionToColor(double dist) {
		// System.err.print("distToSatisfactionToColor(" + dist + ") = ");
		double val = ((dist > 1) ? 1.0 : dist) * 50 + 50;
		double hue = Math.floor((100.0 - val) * 120 / 100); // go from green to red
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
			out.println("          <y:Fill color=\"" + filter.atomToColor(atomName) + "\" transparent=\"false\"/>");
			out.println(
					"          <y:NodeLabel alignment=\"center\" fontsize=\"15\" textColor=\"#000000\" visible=\"true\">"
							+ atomName + "</y:NodeLabel>");
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
			out.println("          <y:NodeLabel alignment=\"center\" fontsize=\"15\" textColor=\""
					+ distToSatisfactionToColor(groundingStatus.get(groundingName)) + "\" visible=\"true\">"
					+ groundingName + "</y:NodeLabel>");
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

				out.println(
						"    <edge id=\"" + (edgeID++) + "\" source=\"" + atom + "\" target=\"" + grounding + "\">");
				out.println("      <data key=\"d9\">");
				out.println("        <y:PolyLineEdge>");
				out.println(
						"          <y:LineStyle color=\"" + colorString + "\" type=\"line\" width=\"" + width + "\"/>");
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

	public HslColor atomToBaseColor(String name) {
		return atomToBaseColor(name, false);
	}

	public HslColor atomToBaseColor(String name, boolean deleted) {
		return filter.atomToBaseColor(name, deleted);
	}

	public boolean isFixed(String atom) {
		return filter.isFixed(atom);
	}

	public Set<String> getAtomNodes() {
		return atomNodes;
	}

	public String getAtomStatus(String atom) {
		return atomStatus.get(atom);
	}

	public Set<String> getGroundingNodes() {
		return groundingNodes;
	}

	public Double getGroundingStatus(String grounding) {
		return groundingStatus.get(grounding);
	}

	public Set<String> getEqualityGroundings() {
		return equalityGroundings;
	}

	public String getLinkStatus(Tuple link) {
		return linkStatus.get(link);
	}

	public Boolean getLinkPressure(Tuple link) {
		if (equalityGroundings.contains(link.get(1))) {
			return true;
		}
		if (!linkToCounterfactual.containsKey(link)) {
			return false;
		}
		return Math.abs(getCounterfactual(link) - distanceToSatisfaction(link.get(1))) > DISSATISFACTION_PRECISION;
	}

	public boolean putsPressureOnGrounding(String atomName, String groundingName) {
		return getLinkPressure(new Tuple(atomName, groundingName));
	}

	public Double getCounterfactual(Tuple link) {
		Double cDist = linkToCounterfactual.get(link);
		if (cDist == null) {
			return null;
		}
		if (cDist < DISSATISFACTION_PRECISION) {
			return 0.0;
		}
		return cDist;
	}

	public Double getCounterfactual(String atomName, String groundingName) {
		return getCounterfactual(new Tuple(atomName, groundingName));
	}

	public double[] getCounterfactualsForEqualityRule(Tuple link) {
		return equalityRuleLinkToCounterfactual.get(link);
	}

	public double[] getCounterfactualsForEqualityRule(String atomName, String groundingName) {
		return getCounterfactualsForEqualityRule(new Tuple(atomName, groundingName));
	}

	public Double getLinkStrength(Tuple link) {
		return linkStrength.get(link);
	}

	public List<Tuple> getIncomingLinks(String grounding) {
		return incomingLinks.get(grounding);
	}
}

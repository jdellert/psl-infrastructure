package de.tuebingen.sfs.psl.engine;

import java.awt.Color;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.term.Constant;

import de.tuebingen.sfs.psl.talk.TalkingPredicate;
import de.tuebingen.sfs.psl.util.color.HslColor;
import de.tuebingen.sfs.psl.util.data.Multimap;
import de.tuebingen.sfs.psl.util.data.Tuple;
import de.tuebingen.sfs.psl.util.data.StringUtils;

public class RagFilter {
	
	public static HslColor BASECOLOR = new HslColor(new Color(255, 214, 51));

	protected Map<String, String> groundPred2ActualNames;
	protected Set<String> ignoreList;
	protected Set<String> ignoreInGui;
	protected Map<String, Double> transparencyMap;

	public RagFilter(Map<String, Double> toneMap) {
		this(toneMap, null);
	}

	public RagFilter(Map<String, Double> toneMap, Map<String, String> groundPred2ActualNames) {
		this.groundPred2ActualNames = groundPred2ActualNames;
		this.ignoreList = new TreeSet<String>();
		this.ignoreInGui = new TreeSet<String>();
		if (toneMap == null) {
			this.transparencyMap = new TreeMap<String, Double>();
		} else {
			this.transparencyMap = toneMap;
		}
	}

	public Set<String> getIgnoreList() {
		return ignoreList;
	}

	public double getValueForAtom(String atomRepresentation) {
		if (transparencyMap == null) {
			return -1.0;
		}
		else
			return transparencyMap.getOrDefault(atomRepresentation, -1.0);
	}

	public double getTransparencyForAtom(String atomRepresentation) {
		if (transparencyMap == null) {
			return 1.0;
		}
		else
			return transparencyMap.getOrDefault(atomRepresentation, 1.0);
	}
	
	public double getToneForAtom(String atomRepresentation) {
		//System.err.println("getToneForAtom(" + atomRepresentation + ")");
		return 1.0 - getTransparencyForAtom(atomRepresentation);
	}

	public double updateToneForAtom(String atomRepresentation, double newTone) {
		if (transparencyMap == null) return 0.0;
		else {
			Double oldTone = transparencyMap.get(atomRepresentation);
			transparencyMap.put(atomRepresentation, newTone);
			if (oldTone == null) return 0.0;
			return 1.0 - oldTone;
		}
	}

	public double strengthToWidth(double strength) {
		double width = strength / 0.1;
		if (width > 4)
			width = 4.0;
		if (width < 1)
			width = 1.0;
		return Math.floor(width);
	}

	public String atomToColor(String name) {
		return "#FFFFFF";
	}
	
	public HslColor atomToBaseColor(String name) {
		return BASECOLOR;
	}
	
	public boolean isRendered(String predName) {
		return !ignoreList.contains(predName);
	}

	public boolean isRenderedInGui(String predName) {
		return !ignoreInGui.contains(predName);
	}

	public String atomToSimplifiedString(GroundAtom atom) {
		String predName = atom.getPredicate().getName();
		//TODO: find out why this wasn't previously necessary
		predName = (groundPred2ActualNames == null)
				? TalkingPredicate.getPredNameFromAllCaps(predName)
				: groundPred2ActualNames.get(predName);
		Tuple argTuple = new Tuple();
		for (Constant c : atom.getArguments()) {
			String cStr = c.toString();
			cStr = cStr.substring(1, cStr.length() - 1);
			argTuple.addElement(cStr);
		}
		return predName + "(" + StringUtils.join(argTuple.toList(),", ") + ")";
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

	public void printNodes(PrintStream out, PslProblem pslProblem) {
		Multimap<String, Tuple> predicatesToTuples = pslProblem.getTuplesByPredicate();
		for (String predName : predicatesToTuples.keySet()) {
			if (!isRendered(predName))
				continue;
			for (Tuple tuple : predicatesToTuples.get(predName)) {
				out.println("    <node id=\"" + predName + "(" + tuple.toString() + ")" + "\">");
				out.println("      <data key=\"d6\">");
				out.println("        <y:ShapeNode>");
				out.println("          <y:Geometry height=\"25\" width=\"150\"/>");
				out.println(
						"          <y:Fill color=\"" + atomToColor(predName + "(" + tuple.toString() + ")") + "\" transparent=\"false\"/>");
				out.println(
						"          <y:NodeLabel alignment=\"center\" fontsize=\"15\" textColor=\"#000000\" visible=\"true\">"
								+ (predName + "(" + tuple.toString() + ")") + "</y:NodeLabel>");
				out.println("          <y:Shape type=\"roundrectangle\"/>");
				out.println("        </y:ShapeNode>");
				out.println("      </data>");
				out.println("    </node>");
			}
		}
	}

	public void printEdges(PrintStream out, Map<String, Double> connectionStrength,
			Map<String, String> connectionColor) {
		int edgeID = 0;
		for (String link : connectionStrength.keySet()) {
			String node1 = link.substring(0, link.indexOf("\t"));
			String node2 = link.substring(link.indexOf("\t") + 1);
			double strength = connectionStrength.get(link);
			double width = strengthToWidth(strength);
			String targetArrow = "delta";
			String colorString = "#c0c0c0";
			colorString = connectionColor.get(link);
			
			String reverseLink = node2 + "\t" + node1;
			
			if (connectionStrength.get(reverseLink) != null) {
				String otherColor = connectionColor.get(reverseLink);			
				if (otherColor.equals(colorString)) {
					if (node1.compareTo(node2) > 0) continue;
					targetArrow = "none";
					strength = Math.max(strength, connectionStrength.get(reverseLink));
				} else {
					if (colorString.equals("#c0c0c0")) continue;
				}
			}
			
			//second version: single link per pair, more difficult to interpret?
			/*String targetArrow = "none";
			if (connectionStrength.get(node2 + "\t" + node1) == null) {
				targetArrow = "delta";
			}
			else {
				if (node1.compareTo(node2) > 0) continue;
			}*/
			
			out.println("    <edge id=\"" + (edgeID++) + "\" source=\"" + node1 + "\" target=\"" + node2 + "\">");
			out.println("      <data key=\"d9\">");
			out.println("        <y:PolyLineEdge>");
			out.println("          <y:LineStyle color=\"" + colorString + "\" type=\"line\" width=\""
					+ width + "\"/>");
			out.println("          <y:Arrows source=\"none\" target=\"" + targetArrow + "\"/>");
			out.println("        </y:PolyLineEdge>");
			out.println("      </data>");
			out.println("    </edge>");
		}
	}

	public void printClosingTags(PrintStream out) {
		out.println("  </graph>");
		out.println("</graphml>");
	}
}

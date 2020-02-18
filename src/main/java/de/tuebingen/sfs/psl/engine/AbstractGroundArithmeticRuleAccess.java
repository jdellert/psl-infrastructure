package de.tuebingen.sfs.psl.engine;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.arithmetic.AbstractGroundArithmeticRule;
import org.linqs.psl.reasoner.function.FunctionComparator;

/**
 * Makes it possible to look inside arithmetic rule objects in the PSL implementation.
 * 
 * Previously, introspection was needed here as a bugfix for PSL version 2.0.0: 
 * in the original version, AbstractGroundArithmeticRule.getAtoms() always returns the empty list!
 * 
 * We are now keeping it as an abstraction layer in case drastic changes happen to the implementation again.
 * 
 * @author jdellert
 *
 */
public class AbstractGroundArithmeticRuleAccess {
	public static double extractConstant(AbstractGroundArithmeticRule rule) {
		return rule.getConstant();
	}
	
	public static FunctionComparator extractComparator(AbstractGroundArithmeticRule rule) {
		return rule.getComparator();
	}
	
	public static List<Double> extractCoefficients(AbstractGroundArithmeticRule rule) {
		List<Double> coeffList = new LinkedList<Double>();
		for (double coeff : rule.getCoefficients()) {
			coeffList.add(coeff);
		}
		return coeffList;
	}
	
	public static List<GroundAtom> extractAtoms(AbstractGroundArithmeticRule rule) {
		List<GroundAtom> atomList = Arrays.asList(rule.getOrderedAtoms());
		return atomList;
	}
}

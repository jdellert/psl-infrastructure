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

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.arithmetic.AbstractGroundArithmeticRule;
import org.linqs.psl.reasoner.function.FunctionComparator;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Makes it possible to look inside arithmetic rule objects in the PSL implementation.
 * <p>
 * Previously, introspection was needed here as a bugfix for PSL version 2.0.0:
 * in the original version, AbstractGroundArithmeticRule.getAtoms() always returns the empty list!
 * <p>
 * We are now keeping it as an abstraction layer in case drastic changes happen to the implementation again.
 *
 * @author jdellert
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

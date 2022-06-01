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
import org.linqs.psl.model.rule.logical.AbstractGroundLogicalRule;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

/**
 * Makes it possible to look inside logical rule objects in the PSL implementation.
 *
 * @author jdellert
 */
public class AbstractGroundLogicalRuleAccess {
    public static List<Double> extractSigns(AbstractGroundLogicalRule rule) {
        List<Double> signs = new LinkedList<Double>();
        try {
            Field fld = AbstractGroundLogicalRule.class.getDeclaredField("posLiterals");
            fld.setAccessible(true);
            List<GroundAtom> posLiterals = (List<GroundAtom>) fld.get(rule);
            for (GroundAtom posLiteral : posLiterals) {
                signs.add(1.0);
            }
            fld = AbstractGroundLogicalRule.class.getDeclaredField("negLiterals");
            fld.setAccessible(true);
            List<GroundAtom> negLiterals = (List<GroundAtom>) fld.get(rule);
            for (GroundAtom negLiteral : negLiterals) {
                signs.add(-1.0);
            }
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return signs;
    }

    public static List<GroundAtom> extractAtoms(AbstractGroundLogicalRule rule) {
        List<GroundAtom> atoms = new LinkedList<GroundAtom>();
        try {
            Field fld = AbstractGroundLogicalRule.class.getDeclaredField("posLiterals");
            fld.setAccessible(true);
            List<GroundAtom> posLiterals = (List<GroundAtom>) fld.get(rule);
            for (GroundAtom posLiteral : posLiterals) {
                atoms.add(posLiteral);
            }
            fld = AbstractGroundLogicalRule.class.getDeclaredField("negLiterals");
            fld.setAccessible(true);
            List<GroundAtom> negLiterals = (List<GroundAtom>) fld.get(rule);
            for (GroundAtom negLiteral : negLiterals) {
                atoms.add(negLiteral);
            }
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return atoms;
    }
}

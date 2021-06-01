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

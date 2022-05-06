package de.tuebingen.sfs.psl.engine;

import de.tuebingen.sfs.psl.talk.TalkingRule;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.groovy.PSLModel;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.parser.ModelLoader;
import org.linqs.psl.parser.RulePartial;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class RuleStore {

    PslProblem pslProblem;
    DataStore dataStore;
    PSLModel model;
    Map<Rule, String> ruleToName;
    Map<String, Rule> nameToRule;
    Map<String, TalkingRule> nameToTalkingRule;

    public RuleStore(PslProblem pslProblem, DataStore dataStore, PSLModel model) {
        this.pslProblem = pslProblem;
        this.dataStore = dataStore;
        this.model = model;
        ruleToName = new HashMap<Rule, String>();
        nameToRule = new TreeMap<String, Rule>();
        nameToTalkingRule = new TreeMap<String, TalkingRule>();
    }

    public void addRule(TalkingRule rule) {
        String ruleName = rule.getName();
        if (nameToRule.containsKey(ruleName)) {
            System.err.println("Rule '" + ruleName + "' already added to this model. Ignoring second declaration. "
                    + "Please make sure to give your rules unique names.");
            return;
        }
        nameToTalkingRule.put(ruleName, rule);
        nameToRule.put(ruleName, rule.getRule());
        ruleToName.put(rule.getRule(), ruleName);
        model.addRule(rule.getRule());
    }

    public void addRule(String ruleName, String ruleString) {
        if (nameToRule.containsKey(ruleName)) {
            System.err.println("Rule '" + ruleName + "' already added to this model. Ignoring second declaration. "
                    + "Please make sure to give your rules unique names.");
            return;
        }
        try {
            RulePartial partial = ModelLoader.loadRulePartial(dataStore, ruleString);
            Rule rule = partial.toRule();

            ruleToName.put(rule, ruleName);
            nameToRule.put(ruleName, rule);
            nameToTalkingRule.put(ruleName, TalkingRule.createTalkingRule(ruleName, ruleString, rule, pslProblem));
            model.addRule(rule);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeRule(String ruleName) {
        if (nameToRule.containsKey(ruleName)) {
            Rule rule = nameToRule.get(ruleName);
            model.removeRule(rule);
            nameToRule.remove(ruleName);
            ruleToName.remove(rule);
        }
    }

    public int size() {
        return nameToRule.size();
    }

    public List<Rule> listRules() {
        List<Rule> ruleList = new LinkedList<Rule>();
        for (Rule rule : model.getRules()) {
            ruleList.add(rule);
        }
        return ruleList;
    }

    public String getNameForRule(Rule rule) {
        return ruleToName.get(rule);
    }

    public Rule getRuleByName(String ruleName) {
        return nameToRule.get(ruleName);
    }

}

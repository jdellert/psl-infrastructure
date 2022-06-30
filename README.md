# PSL Infrastructure

This library provides an API for the [Probabilistic Soft Logic](https://psl.linqs.org/) (PSL) framework to allow programmatically defining PSL problems as well as using several other functionalities:

## Features

### Rule-atom Graph
Each PSL problem's [RuleAtomGraph](https://github.com/jdellert/psl-infrastructure/blob/master/src/main/java/de/tuebingen/sfs/psl/engine/RuleAtomGraph.java) (the graph connecting a ground rule with the associated ground atoms) is extracted after the inference is complete.

- The method `putsPressureOnGrounding` indicates whether a given ground rule is active with respect to the value of a given ground atom in the MAP state.
- `distanceToSatisfaction` returns a grounding's distance to satisfaction.
- `getCounterfactual` provides information on whether a given ground rule would be violated if the score of a given atom were modified slightly.

### Talking rules/constraints and predicates
The subpackages [`talk`](https://github.com/jdellert/psl-infrastructure/tree/master/src/main/java/de/tuebingen/sfs/psl/talk),
[`talk.pred`](https://github.com/jdellert/psl-infrastructure/tree/master/src/main/java/de/tuebingen/sfs/psl/talk/pred), 
and [`talk.rule`](https://github.com/jdellert/psl-infrastructure/tree/master/src/main/java/de/tuebingen/sfs/psl/talk/rule) allow the definition of PSL problems with human-understandable inference explanations.

If you define a custom rule class, it needs to extend one of the following classes:

- `TalkingLogicalRule`: weighted logical rules
- `TalkingLogicalConstraint`: logical constraints
- `TalkingArithmeticRule`: weighted arithmetic rules
- `TalkingArithmeticConstraint`: arithmetic constraints

Overriding a rule's `generateExplanation` code allows creating custom templates for explaining the rule's logic in the context of a selected associated ground atom.
Otherwise, default logic for generating explanations for various rule types exists.

To create predicates that can easily be rendered as noun phrases or sentences, extend the [TalkingPredicate](https://github.com/jdellert/psl-infrastructure/blob/master/src/main/java/de/tuebingen/sfs/psl/talk/pred/TalkingPredicate.java) class.

### Grounding

Extend the [PslProblem](https://github.com/jdellert/psl-infrastructure/blob/master/src/main/java/de/tuebingen/sfs/psl/engine/PslProblem.java) class to declare the predicates and rules that should be used for an inference.
Extend the [IdeaGenerator](https://github.com/jdellert/psl-infrastructure/blob/master/src/main/java/de/tuebingen/sfs/psl/engine/IdeaGenerator.java) to write code for generating ground atoms.

### Parallel inferences
The [PartitionManager](
https://github.com/jdellert/psl-infrastructure/blob/master/src/main/java/de/tuebingen/sfs/psl/engine/PartitionManager.java) allows for several PSL inferences to be run at once if they use disjoint sets of (target) atoms.
(We are also working on a queuing mechanism for problems with overlapping atom sets.)

### Model evaluations

The [`eval`](https://github.com/jdellert/psl-infrastructure/tree/master/src/main/java/de/tuebingen/sfs/psl/eval) package allows evaluating inference results against a gold standard.

## Example

Please refer to the repository's wiki for a [walkthrough of a simple sample project](https://github.com/jdellert/psl-infrastructure/wiki/Example:-Lives-&-Knows).

## Related libraries

The [psl-ragviewer](https://github.com/verenablaschke/psl-ragviewer) library contains code for inspecting the inference results created with the code in this repository in a GUI.
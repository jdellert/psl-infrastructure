# PSL Infrastructure

This package provides an API for the [Probabilistic Soft Logic](https://psl.linqs.org/) (PSL) framework to allow programmatically defining PSL problems as well as using several other functionalities:

## Rule-atom Graph
Each PSL problem's [`RuleAtomGraph`](https://github.com/jdellert/psl-infrastructure/blob/master/src/main/java/de/tuebingen/sfs/psl/engine/RuleAtomGraph.java) (the graph connecting a ground rule with the associated ground atoms) is extracted after the inference is complete.

- The method `putsPressureOnGrounding` indicates whether a given ground rule is active with respect to the value of a given ground atom in the MAP state.
- `distanceToSatisfaction` returns a grounding's distance to satisfaction.
- `getCounterfactual` provides information on whether a given ground rule would be violated if the score of a given atom were modified slightly.

## Talking rules/constraints and predicates
The subpackages `talk`, `talk.pred`, and `talk.rule` allow the definition of PSL problems with human-understandable inference explanations.

## Parallel inferences / inference queuing

## Model evaluations

## Project structure
PslProblem, IdeaGenerator etc.


Reference to psl-ragviewer


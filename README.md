# Fact Checker

## Approach

Our approach is based on [this paper](https://aclanthology.org/2020.coling-main.147.pdf).
The paper does not provide any code that implements the proposed algorithm.
It proposes a rule-based approach to fact-checking, where rules are generated from example triples.
A rule consists of a body and a head,
where the body is a set of connected triples that form a path and are used to infer the head triple.
The rules are then used to classify new triples as true or false, based on the existing knowledge graph.

We skipped the generation of examples in our implementation and used the provided training set.

Our implementation uses Apache Jena's custom rule inferring capability
(https://jena.apache.org/documentation/inference/#rules) for rule evaluation.
The rule generation uses Jena's [ARQ engine](https://jena.apache.org/documentation/query/index.html) for the SPARQL evaluation.

### Rule Generation

Rule generation is based on the provided example triples and works in 3 steps:

- from each example triple create a graph that consists of paths between the subject and object of the example triple, each path has a length less than or equal to a configured maximum path length
- from the local graph extract all paths that connect the subject and object of the example triple
- generate rules from the paths by replacing the resources in the paths with variables

Examples that have a truth value of $1$ lead to positive rules (that support the veracity of the triple to be checked)
and examples that have a truth value of $0$ lead to negative rules (that oppose the veracity of the triple to be checked).

Note that we skipped adding the additional unequal predicates to the local graph mentioned in the referenced paper.

### Rule Weighting

The weight of a rule represents its effectiveness in classifying statements correctly as true or false.
The weight is calculated based on the number of examples
the rule actually covers and the number of examples that have the same predicate as the head of the rule,
i.e., the total number of examples the rule could cover.
This is done with both positive and negative examples for each rule.

The calculation slightly differs from the paper, as we're not using the ration between the number of
covered examples and covered examples by the unbound rule, 
but the ratio between covered examples and number of examples with the same predicate as the head of the rule.

The weight $w_2(r)$ of a rule $r$ is calculated as follows:
$$w_2(r) = \alpha \cdot \left(1 - \frac{\left| C_r(E_{correct}) \right|}{\left| P_r(E_{correct}) \right|} \right) + \beta \left(\frac{\left| C_r(E_{counter}) \right|}{\left| P_r(E_{counter}) \right|} \right)$$

Whereas:
- $\alpha$ and $\beta$ are each real constants between $0$ and $1$ whose sum equals $1$
- $C_r(E_{correct})$ is the number of correct examples that are covered by the rule $r$
- $P_r(E_{correct})$ is the number of correct examples that share the same predicate as the head of $r$ and therefore could be covered by the rule $r$
- $C_r(E_{counter})$ is the number of counter-examples that are covered by the rule $r$
- $P_r(E_{counter})$ is the number of counter-examples that share the same predicate as the head of $r$ and therefore could be covered by the rule $r$

If a rule is positive,
the correct examples are the examples with a true statement, and the counter-examples are negative statements.
If a rule is negative, this is reversed.

We also noticed that the formula for the counter-weight $w_c(r)$ of a rule $r$, 
as presented in the paper, may produce invalid values for the weight (weights greater than $1$),
if the number of covered counter-examples is greater than the number of covered correct examples.
For this reason, we simply added a cap to the formula to prevent this.

### Fact Checking

To check a triple, we calculate a veracity value for it, the same way as presented in the paper.
To do this, 
we first search for the lowest weighted positive rule that infers the triple.
If no positive rule infers the triple, we search for the lowest weighted negative rule that infers the triple.

In difference to the paper, we initialize both weights with $1.0$.
If a positive rule covering the triple is found, and the negative weight is set to $0.0$,
as stated in the paper, the veracity of a triple will never be greater than $0.5$.

## Build requirements

The system requires to have `java 21` installed. Other requirements are managed by Gradle.

## Build Instructions

To build a single jar file with all dependencies, run the following command:

```shell
$ ./gradlew shadowJar
```

It may be required to make the `gradlew` file executable by running `chmod +x gradlew` before executing the command.

The jar file will be located at `app/build/libs/upb_sw_factChecking.jar`.

## Run Instructions

To run the compiled jar file, execute the following command:

```shell
java -jar upb_sw_factChecking.jar
```

## Usage

The application has two commands `check` and `evaluate`.
The `check` command checks the correctness of rdf statements in the given test file.
The `evaluate` command evaluates the systems performance against a training set.
Rules are generated automatically when started the first time and then saved to a file.
Subsequent executions will load and use these previously stored rules.

```shell
java -jar upb_sw_factChecking.jar check    ( fokgsw | --test-file FILE ) --dump-file FILE [ --output-file FILE ]
java -jar upb_sw_factChecking.jar evaluate ( fokgsw | --test-file FILE ) --dump-file FILE [ --output-file FILE ]
```
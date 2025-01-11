# Fact Checking

## Build Instructions

To build a single jar file with all dependencies, run the following command:

```shell
$ ./gradlew shadowJar
```

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

```shell
java -jar upb_sw_factChecking.jar check    ( fokgsw | --test-file FILE )  ( --endpoint URL | --dump-file FILE ) --owl-file FILE [ --output-file FILE ]
java -jar upb_sw_factChecking.jar evaluate ( fokgsw | --test-file FILE )  ( --endpoint URL | --dump-file FILE ) --owl-file FILE [ --output-file FILE ]
```
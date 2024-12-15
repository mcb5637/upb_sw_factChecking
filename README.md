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

```shell
java -jar upb_sw_factChecking.jar check    [ fokgsw | --test-file  ... ]  [ --endpoint URL | --rdf-file ... ] [ --ontology-file ... ] [ --output-file ... ]
java -jar upb_sw_factChecking.jar evaluate [ fokgsw | --train-file ... ]  [ --endpoint URL | --rdf-file ... ] [ --ontology-file ... ] [ --output-file ... ]
```
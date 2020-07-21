# About #

A small command-line Java application for recording decision paths during the evaluation of a decision tree ensemble model.

# Installation #

Build using Apache Maven:

```
$ mvn clean package
```

The build produces an executable uber-JAR file `target/recordcount-executable-1.0-SNAPSHOT.jar`.

# Development #

Initialize [Eclipse IDE](https://www.eclipse.org/ide/) project using Apache Maven:

```
$ mvn eclipse:eclipse
```

Import the project into Eclipse IDE using the menu path `File -> Import... -> General/Existing Projects into Workspace (-> Select root directory -> Finish)`.

The project should be now visible as `rf_recordcount` under "Project explorer" and/or "Package explorer" views.

# Usage #

The resources folder [`src/main/resources`](https://github.com/vruusmann/rf_recordcount/tree/master/src/main/resources) contains Scikit-Learn examples.

Getting help:

```
$ java -jar target/recordcount-executable-1.0-SNAPSHOT.jar --help
```

Scoring the Scikit-Learn random forest example with the full `Audit` dataset:

```
$ java -jar target/recordcount-executable-1.0-SNAPSHOT.jar --pmml-input src/main/resources/pmml/RandomForestAudit.pmml --pmml-output Audit.pmml --csv-input src/main/resources/csv/Audit.csv
```

Scoring the Scikit-Learn random forest example with the first 100 data records of the `Auto` dataset:

```
$ head -n 101 src/main/resources/csv/Auto.csv > Auto-small.csv
$ java -jar target/recordcount-executable-1.0-SNAPSHOT.jar --pmml-input src/main/resources/pmml/RandomForestAuto.pmml --pmml-output Auto-small.pmml --csv-input Auto-small.csv
```
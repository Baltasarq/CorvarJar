# CorvarJar
A library extracted from the Corvar project in order to be able to integrate the analysis in other programs.

# Compilation
These sources are prepared as a **IntelliJ IDEA**'s project. You can, however, create a new project using the src/ directory. Or just compile all the files with something like:
```
$ cd src
$ javac *.java
$ jar cf corvarjar.jar *.java
```

# Integration in other projects
CorvarJar is a library prepared to be embedded in other projects.
The way to use it is to just create a **ResultAnalyzer** object passing the name of the *.res* file, call **ResultAnalyzer**.*analyze()* and then get the whole report with **ResultAnalyzer**.*getReport()* or just the stress level with **ResultAnalyzer**.*getStressLevel()*. For example:

```
ResultAnalyzer resultAnalyzer = new ResultAnalyzer( args[ 0 ] );

resultAnalyzer.analyze();

System.out.println( "Stress level: " + resultAnalyzer.getStressLevel() );
System.out.println( "MadRR: " + resultAnalyzer.getMadRR() + "ms." );
System.out.println( "Entropy (ApEn): " + resultAnalyzer.getApEn() );
System.out.print( "Probstress  : " + resultAnalyzer.getProbStress() );
System.out.println( "\n\t(values > 0.5 indicate stress.)" );
```


# Credits

STARS Project

Bahia Software & UVigo's MILE group.

(c) Baltasar 2020 MIT License <jbgarcia@uvigo.es>

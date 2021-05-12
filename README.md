# JReduce Plugin

## How to Run This Plugin

* prerequisite: java 8, maven
* compile: `mvn package`
* run `mvn exec:java -Dexec.mainClass="Main" -Dexec.args="..."`
* - commands for sample
```shell
# if target is present
rm -rf $PATH_HERE/sample/target
mvn exec:java -Dexec.mainClass="Main" -Dexec.args="-w '$PATH_HERE/sample/target' -l '$PATH_HERE/sample/lib' -c '$PATH_HERE/sample/classes' -p '$PATH_HERE/sample/predicate.sh' -t '$PATH_HERE/sample/target'"
```    

## Configs

* check `main/java/helper/GlobalConfig.java` for log options
* check `main/jvm/ClassAnalyzeOptions.java` for reduction options, pass this to `ClassPool::readClasses` for reduction preparation
* - `addHierarchy` add hierarchy information to global hierarchy
* - `doReduction` enable reduction work
* - `checkClassAdapter` enable ASM's `ClassAdapter` check
* - `addMethodRemoval` enable method body bytecode reduction, doesn't work with `addParamSubtyping`
* - `addParamSubtyping` enable method parameter reduction, doesn't work with `addMethodRemoval`
* - `addParentCollapsing` enable class hierarchy collapsing reduction, currently broken.

## Input/Output

* rewrite `main/java/reduction/WorkingEnv.java` for inputting following folders and files
* - source folder
* - library folder
* - target folder
* - JReducePredicate, currently using shell script from JReduce

## How to Run

* For JReduce artifact
* - check `main/java/reduction/WorkingEnv.java` for reading paths
* - Call `runAll` or `runWith` in `main/java/Main.java`
* - Run JReduce with copied `JReducePredicate.sh` and `compile.sh` (modify `run.sh` from JReduce)
* Custom bytecode lib
* - rewrite `WorkingEnv.java` with building `ClassPool`, `Predicate`




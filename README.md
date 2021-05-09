# JReduce Plugin

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
* - predicate, currently using shell script from JReduce

## How to Run

* For JReduce artifact
* - check `main/java/reduction/WorkingEnv.java` for reading paths
* - Call `runAll` or `runWith` in `main/java/Main.java`
* - Run JReduce with copied `predicate.sh` and `compile.sh` (modify `run.sh` from JReduce)
* Custom bytecode lib
* - rewrite `WorkingEnv.java` with building `ClassPool`, `Predicate`
* - for non-decompiler cases, replace method call results with `null` or default values (stubbing) may provide a lot of invalid values: could use a reflection mechanism to create a valid value?




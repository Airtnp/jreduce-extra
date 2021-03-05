package jvm;

public class ClassAnalyzeOptions {
    public boolean doReduction = true;
    public boolean addHierarchy = true;

    public boolean checkClassAdapter = true;

    public boolean addMethodRemoval = true;
    public boolean addInitMethodRemoval = true;
    public boolean doMethodWithTryCatch = true;

    public boolean addParamSubtyping = false;
    public boolean addParentCollapsing = false;
}

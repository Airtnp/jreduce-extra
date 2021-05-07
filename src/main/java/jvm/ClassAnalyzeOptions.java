package jvm;

public class ClassAnalyzeOptions {
    public boolean doReduction = true;
    public boolean addHierarchy = true;

    public boolean checkClassAdapter = true;

    public boolean addMethodRemoval = true;
    public boolean addInitMethodRemoval = false;
    public boolean doMethodWithTryCatch = true;

    public boolean addParamSubtyping = false;
    // Not working now.
    public boolean addParentCollapsing = false;
}

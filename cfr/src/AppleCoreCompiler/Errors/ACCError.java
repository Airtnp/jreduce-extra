/*
 * Decompiled with CFR 0_132.
 */
package AppleCoreCompiler.Errors;

public abstract class ACCError
extends Exception {
    private String sourceFileName;
    private int lineNumber;

    public ACCError(String string, String string2, int n) {
        super(string);
        this.sourceFileName = string2;
        this.lineNumber = n;
    }

    public ACCError(String string) {
        this(string, null, 0);
    }
}


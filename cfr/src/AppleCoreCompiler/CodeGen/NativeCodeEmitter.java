/*
 * Decompiled with CFR 0_132.
 */
package AppleCoreCompiler.CodeGen;

import java.io.PrintStream;

public abstract class NativeCodeEmitter {
    public PrintStream printStream;

    public NativeCodeEmitter(PrintStream printStream) {
        this.printStream = printStream;
    }

    public void emit(String string) {
    }
}


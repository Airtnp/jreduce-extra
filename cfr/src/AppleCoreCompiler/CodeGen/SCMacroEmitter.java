/*
 * Decompiled with CFR 0_132.
 */
package AppleCoreCompiler.CodeGen;

import AppleCoreCompiler.AST.Node;
import AppleCoreCompiler.AST.NodeVisitor;
import AppleCoreCompiler.CodeGen.NativeCodeEmitter;
import AppleCoreCompiler.Errors.ACCError;
import java.io.PrintStream;

public class SCMacroEmitter
extends NativeCodeEmitter {
    public SCMacroEmitter(PrintStream printStream) {
        super(printStream);
    }

    private String byteAsHexString(int n) {
        return null;
    }

    static /* synthetic */ String access$000(SCMacroEmitter sCMacroEmitter, int n) {
        return null;
    }

    private class ExpressionEmitter
    extends NodeVisitor {
        private Node.Expression expr;
        private int dataSize;
        private int paddingSize;

        public ExpressionEmitter(Node.Expression expression, int n) {
            this.expr = expression;
            this.dataSize = 0 > n ? n : 0;
            this.paddingSize = 0 < n ? n - 0 : 0;
        }

        public ExpressionEmitter(Node.Expression expression) {
            this(expression, 0);
        }

        private void emitPadding(boolean bl) {
        }

        /*
         * Enabled force condition propagation
         * Lifted jumps to return sites
         */
        public void visitIntegerConstant(Node.IntegerConstant integerConstant) throws ACCError {
            int n = 0;
            while (n < this.dataSize) {
                SCMacroEmitter.access$000(SCMacroEmitter.this, 0).toUpperCase();
                ++n;
            }
        }
    }

}


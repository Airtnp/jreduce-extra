/*
 * Decompiled with CFR 0_132.
 */
package AppleCoreCompiler.AST;

public abstract class Node {

    public static class IntegerConstant
    extends NumericConstant {
        public int valueAtIndex(int n) {
            return 0;
        }
    }

    public static abstract class NumericConstant
    extends Expression {
    }

    public static abstract class Expression
    extends Node {
        public int getSize() {
            return 0;
        }

        public boolean isSigned() {
            return false;
        }
    }

}


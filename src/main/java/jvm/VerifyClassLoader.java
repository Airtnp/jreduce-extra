package jvm;

import java.lang.ClassLoader;


class VerfiyClassLoader extends ClassLoader {
    final String className;
    final byte[] bytecode;

    public VerfiyClassLoader(final String className, final byte[] bytecode) {
        this.className = className;
        this.bytecode = bytecode;
    }

    @Override
    public Class<?> loadClass(String _name) throws ClassNotFoundException {
        return defineClass(className, bytecode, 0, bytecode.length);
    }
}

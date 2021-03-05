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
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (!className.equals(name)) {
            return super.loadClass(name);
        }
        return defineClass(className, bytecode, 0, bytecode.length);
    }
}

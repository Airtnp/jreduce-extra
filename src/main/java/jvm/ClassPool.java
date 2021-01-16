package jvm;

import graph.Hierarchy;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;

import static org.objectweb.asm.Opcodes.ASM9;

public class ClassPool {
    final String inputPath;
    final String libPath;
    final String outputPath;
    final boolean isReplaceAll;
    final HashMap<Path, ClassReader> clsPool;

    public ClassPool(final String inputPath, final String libPath, final String outputPath, final boolean isReplaceAll) {
        this.inputPath = inputPath;
        this.libPath = libPath;
        this.outputPath = outputPath;
        this.isReplaceAll = isReplaceAll;
        this.clsPool = new HashMap<>();
    }

    public void readLibs(final Hierarchy hierarchy) throws IOException {
        Files.walk(Paths.get(libPath))
                .filter(Files::isRegularFile)
                .filter((classfile) -> classfile.toString().endsWith(".class"))
                .forEach((classfile) -> {
                    final byte[] bytes;
                    try {
                        bytes = Files.readAllBytes(classfile);
                        if (bytes.length != 0) {
                            final ClassReader cr = new ClassReader(bytes);
                            final ClassNode cn = new LibCollector(hierarchy, null);
                            cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    public void readClasses(final Hierarchy hierarchy) throws IOException {
        Files.walk(Paths.get(inputPath))
                .filter(Files::isRegularFile)
                .filter((classfile) -> classfile.toString().endsWith(".class"))
                .forEach((classfile) -> {
                    final Path relPath = Paths.get(inputPath).relativize(classfile);
                    final byte[] bytes;
                    try {
                        bytes = Files.readAllBytes(classfile);
                        final ClassReader cr = new ClassReader(bytes);
                        clsPool.put(relPath, cr);
                        final ClassNode cn = new StubCollector(hierarchy, null);
                        cr.accept(cn, ClassReader.SKIP_DEBUG);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    public void writeClasses(final Hierarchy hierarchy, final boolean isInitial, final HashSet<Integer> closure) {
        clsPool.forEach((key, cr) -> {
            final Path output = Paths.get(outputPath).resolve(key);
            try {
                Files.createDirectories(output.getParent());
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Compute the stack map frames of methods from scratch
            final ClassWriter cw = new HierarchyClassWriter(hierarchy, 0, libPath, inputPath);
            final ClassVisitor cv = new ClassVisitor(ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    final MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new StubCallVisitor(ASM9, cr.getClassName(), isInitial, isReplaceAll, mv, hierarchy, closure);
                }
            };

            // Ignore SourceFile, SourceDebugExtension, LocalVariableTable, LocalVariableTypeTable, LineNumberTable, MethodParameters
            // Don't need to skip stack map frame... ASM can adjust the offset automatically
            cr.accept(cv, ClassReader.SKIP_DEBUG);

            final byte[] outputBytes = cw.toByteArray();
            try {
                Files.write(output, outputBytes);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }

            hierarchy.visitEndClass(isInitial);
        });
        hierarchy.visitEndClassPool();
    }

    public void identityWriteClasses(final Hierarchy hierarchy) {
        clsPool.forEach((key, cr) -> {
            final Path output = Paths.get(outputPath).resolve(key);
            try {
                Files.createDirectories(output.getParent());
            } catch (IOException e) {
                e.printStackTrace();
            }

            final ClassWriter cw = new HierarchyClassWriter(hierarchy, 0, libPath, inputPath);
            // Ignore SourceFile, SourceDebugExtension, LocalVariableTable, LocalVariableTypeTable, LineNumberTable, MethodParameters
            // Don't need to skip stack map frame... ASM can adjust the offset automatically
            cr.accept(cw, ClassReader.SKIP_DEBUG);

            final byte[] outputBytes = cw.toByteArray();
            try {
                Files.write(output, outputBytes);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        });
    }
}

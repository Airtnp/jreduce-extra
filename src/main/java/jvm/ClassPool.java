package jvm;

import graph.Hierarchy;
import helper.GlobalConfig;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.SortedSet;

import static org.objectweb.asm.Opcodes.ASM9;

public class ClassPool {
    final Path inputPath;
    final Path libPath;
    final Path outputPath;
    final HashMap<Path, ClassAnalyzer> caPool;
    final HashMap<Path, ClassReader> clsPool;

    public ClassPool(final Path inputPath, final Path libPath, final Path outputPath) {
        this.inputPath = inputPath;
        this.libPath = libPath;
        this.outputPath = outputPath;
        this.clsPool = new HashMap<>();
        this.caPool = new HashMap<>();
    }

    public void readLibs(final Hierarchy hierarchy) throws IOException {
        Files.walk(libPath)
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

    public void readClassesSingle(final Path classPath, final Hierarchy hierarchy, final ClassAnalyzeOptions options) throws IOException {
        Files.walk(inputPath)
                .filter(Files::isRegularFile)
                .filter((classfile) -> classfile.toString().endsWith(".class"))
                .forEach((classfile) -> {
                    final Path relPath = inputPath.relativize(classfile);
                    final byte[] bytes;
                    try {
                        bytes = Files.readAllBytes(classfile);
                        final ClassReader cr = new ClassReader(bytes);
                        if (classPath.equals(relPath)) {
                            options.doReduction = true;
                        }
                        final ClassAnalyzer cn = new ClassAnalyzer(hierarchy, options,null);
                        // Ignore SourceFile, SourceDebugExtension, LocalVariableTable, LocalVariableTypeTable, LineNumberTable, MethodParameters
                        // Don't need to skip stack map frame... ASM can adjust the offset automatically
                        // unless adding jump instructions
                        cr.accept(cn, ClassReader.SKIP_DEBUG);
                        options.doReduction = false;
                        clsPool.put(relPath, cr);
                        caPool.put(relPath, cn);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    public void readClasses(final Hierarchy hierarchy, final ClassAnalyzeOptions options) throws IOException {
        Files.walk(inputPath)
                .filter(Files::isRegularFile)
                .filter((classfile) -> classfile.toString().endsWith(".class"))
                .forEach((classfile) -> {
                    final Path relPath = inputPath.relativize(classfile);
                    final byte[] bytes;
                    try {
                        bytes = Files.readAllBytes(classfile);
                        final ClassReader cr = new ClassReader(bytes);
                        final ClassAnalyzer cn = new ClassAnalyzer(hierarchy, options,null);
                        // Ignore SourceFile, SourceDebugExtension, LocalVariableTable, LocalVariableTypeTable, LineNumberTable, MethodParameters
                        // Don't need to skip stack map frame... ASM can adjust the offset automatically
                        // unless adding jump instructions
                        cr.accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                        clsPool.put(relPath, cr);
                        caPool.put(relPath, cn);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    public boolean writeClasses(final Hierarchy hierarchy, final SortedSet<Integer> closure, final boolean isFinal) {
        for (final Path key: clsPool.keySet()) {
            final ClassReader cr = clsPool.get(key);
            final ClassAnalyzer ca = caPool.get(key);
            final Path output = outputPath.resolve(key);
            try {
                Files.createDirectories(output.getParent());
            } catch (IOException e) {
                e.printStackTrace();
            }

            final ClassNode reader = new ClassNode(ASM9);
            cr.accept(reader, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            // Compute the stack map frames of methods from scratch
            final ClassWriter cw = new HierarchyClassWriter(hierarchy, ClassWriter.COMPUTE_FRAMES, libPath, inputPath);

            // If check fails, return
            final boolean valid = ca.accept(cw, reader, closure);
            if (!valid) {
                return false;
            }

            if (GlobalConfig.debugClassVerifier) {
                final ClassReader newCr = new ClassReader(cw.toByteArray());
                CheckClassAdapter.verify(newCr, true, new PrintWriter(System.out));
            }

            final byte[] outputBytes = cw.toByteArray();

            if (GlobalConfig.debugClassLoader) {
                final String className = key.replace('/', '.');
                final VerfiyClassLoader loader = new VerfiyClassLoader(className, cw.toByteArray());
                try {
                    loader.loadClass(className);
                } catch (final ClassFormatError cfe) {
                    GlobalConfig.println("Invalid class bytecode: " + cfe);
                    return false;
                }
            }

            try {
                Files.write(output, outputBytes);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
        return true;
    }

    public void identityWriteClasses(final Hierarchy hierarchy) {
        clsPool.forEach((key, cr) -> {
            final Path output = outputPath.resolve(key);
            try {
                Files.createDirectories(output.getParent());
            } catch (IOException e) {
                e.printStackTrace();
            }

            final ClassWriter cw = new HierarchyClassWriter(hierarchy, ClassWriter.COMPUTE_MAXS, libPath, inputPath);
            // Identity write
            cr.accept(cw, 0);

            final byte[] outputBytes = cw.toByteArray();
            try {
                Files.write(output, outputBytes);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        });
    }
}

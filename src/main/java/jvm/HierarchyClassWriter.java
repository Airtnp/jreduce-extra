package jvm;

import graph.ClassVertex;
import graph.Hierarchy;
import org.jgrapht.alg.lca.NaiveLCAFinder;
import org.jgrapht.graph.DefaultEdge;
import org.objectweb.asm.ClassWriter;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class HierarchyClassWriter extends ClassWriter {
    public final Path libPath;
    public final Path bytePath;
    public final Hierarchy hierarchy;

    public HierarchyClassWriter(final Hierarchy hierarchy, final int flags,
                                final Path libPath, final Path bytePath) {
        super(flags);
        this.hierarchy = hierarchy;
        this.libPath = libPath;
        this.bytePath = bytePath;
    }

    @Override
    protected ClassLoader getClassLoader() {
        final ClassLoader classLoader = super.getClassLoader();
        try {
            return new URLClassLoader(
                    new URL[]{libPath.toUri().toURL(), bytePath.toUri().toURL()}, classLoader);
        } catch (MalformedURLException e) {
            return classLoader;
        }
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            return super.getCommonSuperClass(type1, type2);
        } catch (final Exception ex) {
            final ClassVertex cv1 = hierarchy.vertices.get(type1);
            final ClassVertex cv2 = hierarchy.vertices.get(type2);
            if (cv1 == null || cv2 == null) {
                return "java/lang/Object";
            }
            final NaiveLCAFinder<ClassVertex, DefaultEdge> lcaFinder = new NaiveLCAFinder<>(hierarchy.graph);
            final ClassVertex v = lcaFinder.getLCA(cv1, cv2);
            if (v == null) {
                return "java/lang/Object";
            }
            return v.name;
        }
    }
}

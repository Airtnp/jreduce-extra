package helper;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.nio.*;
import org.jgrapht.nio.dot.*;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class JGraphTUtils {
    public static void printInsnGraph(Graph<AbstractInsnNode, DefaultEdge> graph, FileWriter writer) {
        DOTExporter<AbstractInsnNode, DefaultEdge> exporter = new DOTExporter<>();
        exporter.setVertexAttributeProvider((v) -> {
            Map<String, Attribute> map = new LinkedHashMap<>();
            map.put("label", DefaultAttribute.createAttribute(ASMUtils.printInsn(v)));
            return map;
        });
        exporter.exportGraph(graph, writer);
    }

    public static void printInsnGraph(Graph<AbstractInsnNode, DefaultEdge> graph, String path) throws IOException {
        FileWriter writer = new FileWriter(path);
        printGraph(graph, writer);
        writer.close();
    }

    public static <T> void printGraph(Graph<T, DefaultEdge> graph, FileWriter writer) {
        DOTExporter<T, DefaultEdge> exporter = new DOTExporter<>();
        exporter.setVertexAttributeProvider((v) -> {
            Map<String, Attribute> map = new LinkedHashMap<>();
            map.put("label", DefaultAttribute.createAttribute(v.toString()));
            return map;
        });
        exporter.exportGraph(graph, writer);
    }

    public static <T> void printGraph(Graph<T, DefaultEdge> graph, String path) throws IOException {
        FileWriter writer = new FileWriter(path);
        printGraph(graph, writer);
        writer.close();
    }
}

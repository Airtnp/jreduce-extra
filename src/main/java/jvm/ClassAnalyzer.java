package jvm;

import graph.ClassVertex;
import graph.Hierarchy;
import helper.GlobalConfig;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeAnnotationNode;
import org.objectweb.asm.util.CheckClassAdapter;
import reduction.ClassSubTypingRP;
import reduction.ParamSubTypingRP;
import reduction.RPGroup;

import java.util.*;

import static org.objectweb.asm.Opcodes.ASM9;

public class ClassAnalyzer extends ClassNode {
    private final ClassAnalyzeOptions options;
    private final Hierarchy hierarchy;
    private ClassVisitor cv;
    private final RPGroup parentRpGroup;
    // ReducePoint section
    private final RPGroup rpSection;

    public ClassAnalyzer(final Hierarchy hierarchy, final ClassAnalyzeOptions options, final ClassVisitor cv) {
        super(ASM9);
        this.options = options;
        this.cv = cv;
        this.hierarchy = hierarchy;
        this.parentRpGroup = new RPGroup(0, 0, 0);
        this.rpSection = new RPGroup(0, 0, 0);
    }

    public ClassVisitor enableChecking() {
        final ClassVisitor cv = this.cv;
        final CheckClassAdapter cca = new CheckClassAdapter(cv, true);
        this.cv = cca;
        return cv;
    }

    public void disableChecking(final ClassVisitor cv) {
        this.cv = cv;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        final MethodNode mn = new MethodAnalyzer(
                access, name, descriptor, signature, exceptions, this.name, hierarchy, options);
        methods.add(mn);
        return mn;
    }

    @Override
    public void visitEnd() {
        this.rpSection.low = hierarchy.getCurrentIndex();
        final ClassVertex cls = new ClassVertex(this.name, this.access,
                this.superName, new HashSet<>(this.interfaces));
        for (MethodNode mn: this.methods) {
            cls.addMethod(mn);
        }

        if (options.addHierarchy) {
            this.hierarchy.addClass(cls);
        }

        if (this.cv != null)
            accept(this.cv);

        methods.forEach((mn) -> ((MethodAnalyzer) mn).prepare());

        if (options.doReduction && options.addParentCollapsing) {
            analyzeHierarchy();
        }

        this.rpSection.high = hierarchy.getCurrentIndex();
    }

    public void analyzeHierarchy() {
        // XXX: requires to replace superclass call to Object.<init>
        // Ignore raw type & generic type
        if (superName.equals("java/lang/Object") || signature != null) {
            this.parentRpGroup.attribute = -1;
            return;
        }
        List<String> parents = hierarchy.getParentClassReverse(superName);

        final Set<String> hierarchyConstraint = new HashSet<>();
        for (final MethodNode m: methods) {
            final Set<String> constraint = ((MethodAnalyzer) m).classTypeConstraint;
            if (constraint != null) {
                hierarchyConstraint.addAll(hierarchyConstraint);
            }
        }

        // can't reduce superclass
        if (hierarchyConstraint.contains(superName)) {
            this.parentRpGroup.attribute = -1;
            return;
        }

        for (int i = parents.size() - 1; i >= 0; i--) {
            if (hierarchyConstraint.contains(parents.get(i))) {
                parents = parents.subList(i, parents.size());
                break;
            }
        }

        final int low = hierarchy.getCurrentIndex();
        // the reduction group will be: base, derived1, derive2, currentCls...
        // non-exist `=>` Object
        for (final String parent : parents) {
            if (parent.equals("java.lang.Object")) {
                continue;
            }
            final int idx = hierarchy.nextIndex();
            hierarchy.addReductionPoint(new ClassSubTypingRP(idx, parent));
        }

        final int high = hierarchy.getCurrentIndex();
        this.parentRpGroup.low = low;
        this.parentRpGroup.high = high;
    }

    public boolean accept(ClassVisitor classVisitor, final ClassNode reader, final SortedSet<Integer> allRp) {
        if (options.checkClassAdapter) {
            classVisitor = new CheckClassAdapter(classVisitor, true);
        }

        final SortedSet<Integer> rp = rpSection.inRange(allRp);

        // reduce parent type
        String superName = reader.superName;
        if (options.addParentCollapsing && parentRpGroup.attribute != -1) {
            final int rpt = parentRpGroup.maxInRange(rp);
            if (rpt == -1) {
                superName = "java.lang.Object";
            } else {
                superName = ((ClassSubTypingRP)hierarchy.getReductionPoint(rpt)).parentType;
            }
        }

        // Visit the header.
        String[] interfacesArray = new String[reader.interfaces.size()];
        reader.interfaces.toArray(interfacesArray);
        classVisitor.visit(reader.version, reader.access, reader.name, reader.signature, superName, interfacesArray);

        // Visit the source.
        if (reader.sourceFile != null || reader.sourceDebug != null) {
            classVisitor.visitSource(reader.sourceFile, reader.sourceDebug);
        }
        // Visit the module.
        if (reader.module != null) {
            reader.module.accept(classVisitor);
        }
        // Visit the nest host class.
        if (reader.nestHostClass != null) {
            classVisitor.visitNestHost(reader.nestHostClass);
        }
        // Visit the outer class.
        if (reader.outerClass != null) {
            classVisitor.visitOuterClass(reader.outerClass, reader.outerMethod, reader.outerMethodDesc);
        }
        // Visit the annotations.
        if (reader.visibleAnnotations != null) {
            for (AnnotationNode annotation : reader.visibleAnnotations) {
                annotation.accept(classVisitor.visitAnnotation(annotation.desc, true));
            }
        }
        if (reader.invisibleAnnotations != null) {
            for (AnnotationNode annotation : reader.invisibleAnnotations) {
                annotation.accept(classVisitor.visitAnnotation(annotation.desc, false));
            }
        }
        if (reader.visibleTypeAnnotations != null) {
            for (TypeAnnotationNode typeAnnotation : reader.visibleTypeAnnotations) {
                typeAnnotation.accept(
                        classVisitor.visitTypeAnnotation(
                                typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, true));
            }
        }
        if (reader.invisibleTypeAnnotations != null) {
            for (TypeAnnotationNode typeAnnotation : reader.invisibleTypeAnnotations) {
                typeAnnotation.accept(
                        classVisitor.visitTypeAnnotation(
                                typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, false));
            }
        }
        // Visit the non standard attributes.
        if (reader.attrs != null) {
            for (org.objectweb.asm.Attribute attr : reader.attrs) {
                classVisitor.visitAttribute(attr);
            }
        }
        // Visit the nest members.
        if (reader.nestMembers != null) {
            for (String nestMember : reader.nestMembers) {
                classVisitor.visitNestMember(nestMember);
            }
        }
        // Visit the permitted subclasses.
        if (reader.permittedSubclasses != null) {
            for (String permittedSubclass : reader.permittedSubclasses) {
                classVisitor.visitPermittedSubclass(permittedSubclass);
            }
        }
        // Visit the inner classes.
        for (org.objectweb.asm.tree.InnerClassNode innerClass : reader.innerClasses) {
            innerClass.accept(classVisitor);
        }
        // Visit the record components.
        if (recordComponents != null) {
            for (org.objectweb.asm.tree.RecordComponentNode recordComponent : reader.recordComponents) {
                recordComponent.accept(classVisitor);
            }
        }
        // Visit the fields.
        for (org.objectweb.asm.tree.FieldNode field : reader.fields) {
            field.accept(classVisitor);
        }
        // Visit the methods.
        for (int i = 0; i < reader.methods.size(); i += 1) {
            final MethodNode methodNode = reader.methods.get(i);
            final MethodAnalyzer methodAnalyzer = (MethodAnalyzer) this.methods.get(i);
            methodAnalyzer.accept(classVisitor, methodNode, methodAnalyzer.rpSection.inRange(rp));
        }
        classVisitor.visitEnd();

        return true;
    }
}

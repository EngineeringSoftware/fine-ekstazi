package org.ekstazi.smethods;

import org.ekstazi.asm.ClassVisitor;
import org.ekstazi.asm.MethodVisitor;
import org.ekstazi.asm.Opcodes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.ekstazi.smethods.Macros.PROJECT_PACKAGE;

public class MethodCallCollectorCV extends ClassVisitor {

    // Name of the class being visited.
    private String mClassName;

    Map<String, Set<String>> methodName2InvokedMethodNames;
    Map<String, Set<String>> method2usage;
    Map<String, Set<String>> hierarchy_parents;
    Map<String, Set<String>> hierarchy_children;
    Map<String, Set<String>> class2ContainedMethodNames;
    Set<String> classesInConstantPool;

    public MethodCallCollectorCV(Map<String, Set<String>> methodName2MethodNames,
                                 Map<String, Set<String>> hierarchy_parents,
                                 Map<String, Set<String>> hierarchy_children,
                                 Map<String, Set<String>> class2ContainedMethodNames,
                                 Set<String> classesInConstantPool
    ) {
        super(Opcodes.ASM5);
        this.methodName2InvokedMethodNames = methodName2MethodNames;
        this.hierarchy_parents = hierarchy_parents;
        this.hierarchy_children = hierarchy_children;
        this.class2ContainedMethodNames = class2ContainedMethodNames;
        this.classesInConstantPool = classesInConstantPool;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        mClassName = name;
    }

    @Override
    public MethodVisitor visitMethod(int access, final String outerName, final String outerDesc, String signature, String[] exceptions) {
        //append arguments to key, remove what after ) of desc
        if (outerName.equals(PROJECT_PACKAGE)){
            return null;
        }
        String outerMethodSig = outerName + outerDesc.substring(0, outerDesc.indexOf(")")+1);
        String key = mClassName + "#" + outerMethodSig;
        Set<String> mInvokedMethods = methodName2InvokedMethodNames.computeIfAbsent(key, k -> new TreeSet<>());
        return new MethodVisitor(Opcodes.ASM5) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (!owner.startsWith("java/") && !owner.startsWith("org/junit/")) {
                    String methodSig = name + desc.substring(0, desc.indexOf(")") + 1);
                    if (class2ContainedMethodNames.getOrDefault(owner, new HashSet<>()).contains(methodSig)) {
                        String invokedKey = owner + "#" + methodSig;
                        mInvokedMethods.add(invokedKey);
                    }else{
                        // find the first parent that implements the method
                        String firstParent = findFirstParent(owner, methodSig);
                        if (!firstParent.equals("")){
                            String invokedKey = firstParent + "#" + methodSig;
                            mInvokedMethods.add(invokedKey);
                        }
                    }
                    if (!methodSig.startsWith("<init>") && !methodSig.startsWith("<clinit>")) {
                        for (String subClass : hierarchy_children.getOrDefault(owner, new HashSet<>())) {
                            if (classesInConstantPool.contains(subClass)) {
                                if (class2ContainedMethodNames.getOrDefault(subClass, new HashSet<>()).contains(methodSig)) {
                                    String invokedKey = subClass + "#" + methodSig;
                                    mInvokedMethods.add(invokedKey);
                                }
                            }
                            // deal with test class in a special way, all the @test method in hierarchy should be considered
                            if(subClass.contains("Test") && owner.contains("Test")){
                                String invokedKey = subClass + "#" +  outerMethodSig;
                                methodName2InvokedMethodNames.computeIfAbsent(invokedKey, k -> new TreeSet<>()).add(key);

//                                method2usage.computeIfAbsent(key, k -> new TreeSet<>()).add(invokedKey);
                            }
                        }

                    }
//	                int INVOKEVIRTUAL = 182;
//				    int INVOKESPECIAL = 183;
//				    int INVOKESTATIC = 184;
//				    int INVOKEINTERFACE = 185;
//				    int INVOKEDYNAMIC = 186; // visitInvokeDynamicInsn
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String desc){
                String field = owner + "#" + name;
                // outerDesc.equals("<init>")
                // non static field would be invoked through constructor
                if ( (opcode == Opcodes.PUTSTATIC && outerName.equals("<clinit>")) || (opcode == Opcodes.PUTFIELD && outerName.equals("<init>"))) {
                    // || hierarchies.getOrDefault(mClassName, new HashSet<>()).contains(owner)
                    if (owner.equals(mClassName)) {
                        Set methods = methodName2InvokedMethodNames.getOrDefault(field, new HashSet<>());
                        methods.add(key);
                        methodName2InvokedMethodNames.put(field, methods);
                    }
                }
                mInvokedMethods.add(field);
//                  Opcodes.GETFIELD, Opcodes.PUTFIELD, Opcodes.GETSTATIC, Opcodes.PUTSTATIC
                super.visitFieldInsn(opcode, owner, name, desc);
            }

        };
    }

    public String findFirstParent(String currentClass, String methodSig){
        for (String parent : hierarchy_parents.getOrDefault(currentClass, new HashSet<>())){
            if (class2ContainedMethodNames.getOrDefault(parent, new HashSet<>()).contains(methodSig)){
                return parent;
            }else{
                String firstParent = findFirstParent(parent, methodSig);
                if (!firstParent.equals("")){
                    return firstParent;
                }
            }
        }
        return "";
    }
}
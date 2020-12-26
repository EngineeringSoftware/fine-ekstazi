package org.ekstazi.methods;

import org.ekstazi.asm.ClassVisitor;
import org.ekstazi.asm.ClassWriter;
import org.ekstazi.asm.MethodVisitor;
import org.ekstazi.asm.Opcodes;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class MethodCallCollectorCV extends ClassVisitor {

    // Name of the class being visited.
    private String mClassName;

    Map<String, Set<String>> methodName2InvokedMethodNames;

    public MethodCallCollectorCV(ClassWriter cw, Map<String, Set<String>> methodName2MethodNames) {
        super(Opcodes.ASM5);
        this.methodName2InvokedMethodNames = methodName2MethodNames;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        mClassName = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, final String outerName, final String outerDesc, String signature, String[] exceptions) {
        String key = mClassName + outerName;
//        if (outerName.equals("<init>") || outerName.equals("<clinit>")) {
//            key = mClassName + "." + key;
//        }

        Set<String> invokedMethods = methodName2InvokedMethodNames.computeIfAbsent(key, k -> new TreeSet<>());

        return new MethodVisitor(Opcodes.ASM5) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                String invokedKey = owner + name;
//                if (name.equals("<init>") || name.equals("<clinit>")) {
//                    invokedKey = owner + "." + invokedKey;
//                }
                invokedMethods.add(invokedKey);
//	                int INVOKEVIRTUAL = 182;
//				    int INVOKESPECIAL = 183;
//				    int INVOKESTATIC = 184;
//				    int INVOKEINTERFACE = 185;
//				    int INVOKEDYNAMIC = 186; // visitInvokeDynamicInsn
                }
            };
        }
    }
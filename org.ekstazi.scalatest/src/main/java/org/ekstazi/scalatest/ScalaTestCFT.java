/*
 * Copyright 2014-present Milos Gligoric
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ekstazi.scalatest;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.ekstazi.agent.Instr;
import org.ekstazi.asm.ClassReader;
import org.ekstazi.asm.ClassVisitor;
import org.ekstazi.asm.ClassWriter;
import org.ekstazi.asm.MethodVisitor;
import org.ekstazi.asm.Opcodes;

public class ScalaTestCFT implements ClassFileTransformer {

    private static class ScalaTestClassVisitor extends ClassVisitor {
        private final String mClassName;

        public ScalaTestClassVisitor(String className, ClassVisitor cv) {
            super(Instr.ASM_API_VERSION, cv);
            this.mClassName = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
            mv = new ScalaTestMethodVisitor(mClassName, mv);
            return mv;
        }
    }

    private static class ScalaTestMethodVisitor extends MethodVisitor {
        private final String mClassName;

        public ScalaTestMethodVisitor(String className, MethodVisitor mv) {
            super(Instr.ASM_API_VERSION, mv);
            this.mClassName = className;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (opcode == Opcodes.INVOKEVIRTUAL && name.equals("newInstance")
                && (mClassName.contains(ScalaTestNames.DISCOVERY_SUITE_VM) || mClassName.contains("ScalaTestTask")) && owner.equals("java/lang/Class")) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ScalaTestNames.SCALATEST_MONITOR_VM, "newInstance",
                        "(Ljava/lang/Class;)Ljava/lang/Object;", false);
            } else {
                mv.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className.startsWith("org/scalatest/tools/")) {
            ClassReader classReader = new ClassReader(classfileBuffer);
            ClassWriter classWriter = new ClassWriter(classReader,
            /* ClassWriter.COMPUTE_FRAMES | */ClassWriter.COMPUTE_MAXS);
            ScalaTestClassVisitor visitor = new ScalaTestClassVisitor(className, classWriter);
            classReader.accept(visitor, 0);
            return classWriter.toByteArray();
        }
        return null;
    }
}

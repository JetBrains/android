/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.swingp.instrumenter;

import com.android.tools.swingp.PaintImmediatelyMethodStat;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import javax.swing.RepaintManager;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

public class RepaintManagerClassTransformer implements ClassFileTransformer {
  private static final String REPAINT_MANAGER_NAME = RepaintManager.class.getCanonicalName().replace('.', '/');

  @Override
  public byte[] transform(ClassLoader loader,
                          String className,
                          Class<?> classBeingRedefined,
                          ProtectionDomain protectionDomain,
                          byte[] classfileBuffer) {
    if (!REPAINT_MANAGER_NAME.equals(className)) {
      return classfileBuffer;
    }

    System.out.println("Transforming RepaintManager...");
    ClassReader reader = new ClassReader(classfileBuffer);
    ClassWriter defaultWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    ClassVisitor visitor = new RepaintManagerVisitor(defaultWriter);

    try {
      reader.accept(visitor, ClassReader.EXPAND_FRAMES);
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return defaultWriter.toByteArray();
  }

  private static class RepaintManagerVisitor extends ClassVisitor {
    private boolean myIsRepaintManager = false;

    public RepaintManagerVisitor(ClassVisitor visitor) {
      super(Opcodes.ASM5, visitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      if (REPAINT_MANAGER_NAME.equals(name)) {
        myIsRepaintManager = true;
      }
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name,
                                     final String desc, final String signature, final String[] exceptions) {
      MethodVisitor defaultVisitor = super.visitMethod(access, name, desc, signature, exceptions);
      if (!myIsRepaintManager || defaultVisitor == null) {
        return defaultVisitor;
      }

      if ("paint".equals(name) && "(Ljavax/swing/JComponent;Ljavax/swing/JComponent;Ljava/awt/Graphics;IIII)V".equals(desc)) {
        return new PaintMethodVisitor(defaultVisitor, access, name, desc);
      }

      return defaultVisitor;
    }
  }

  private static class PaintMethodVisitor extends GeneratorAdapter {
    private final Type paintImmediatelyMethodStatType = Type.getType(PaintImmediatelyMethodStat.class);
    private int myPaintImmediatelyMethodStatIndex = -1;

    public PaintMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
      super(Opcodes.ASM5, mv, access, name, desc);
      System.out.println("\t...instrumenting " + name);
    }

    @Override
    public void visitCode() {
      super.visitCode();

      newInstance(paintImmediatelyMethodStatType);
      dup();
      loadThis(); // Prepare the stack for the eventual call to the constructor of PaintImmediateMethodStat.
      super.visitVarInsn(Opcodes.ALOAD, 2); // Load the bufferComponent to capture what heavy weight component we're really painting into.
      super.visitVarInsn(Opcodes.ALOAD, 3); // Load the Graphics parameter for the method stat.
      super.visitVarInsn(Opcodes.ILOAD, 4); // Load x.
      super.visitVarInsn(Opcodes.ILOAD, 5); // Load y.
      super.visitVarInsn(Opcodes.ILOAD, 6); // Load w.
      super.visitVarInsn(Opcodes.ILOAD, 7); // Load h.
      invokeConstructor(paintImmediatelyMethodStatType, Method.getMethod(PaintImmediatelyMethodStat.class.getConstructors()[0]));
      myPaintImmediatelyMethodStatIndex = newLocal(paintImmediatelyMethodStatType);
      storeLocal(myPaintImmediatelyMethodStatIndex);
    }

    @Override
    public void visitInsn(int opcode) {
      switch (opcode) {
        case Opcodes.IRETURN:
        case Opcodes.LRETURN:
        case Opcodes.FRETURN:
        case Opcodes.DRETURN:
        case Opcodes.ARETURN:
        case Opcodes.RETURN:
        case Opcodes.ATHROW:
          assert myPaintImmediatelyMethodStatIndex > 0;
          loadLocal(myPaintImmediatelyMethodStatIndex); // Load the PaintChildrenMethodStat local variable onto the stack (it takes no parameters).
          invokeVirtual(paintImmediatelyMethodStatType, new Method("endMethod", "()V"));
          break;

        default:
          // Do nothing special.
          break;
      }
      super.visitInsn(opcode);
    }
  }
}

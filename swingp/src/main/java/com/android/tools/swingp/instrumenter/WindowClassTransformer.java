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

import com.android.tools.swingp.WindowPaintMethodStat;
import java.awt.Window;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

public class WindowClassTransformer implements ClassFileTransformer {
  private static final String WINDOW_NAME = Window.class.getCanonicalName().replace('.', '/');

  @Override
  public byte[] transform(ClassLoader loader,
                          String className,
                          Class<?> classBeingRedefined,
                          ProtectionDomain protectionDomain,
                          byte[] classfileBuffer) {
    if (!WINDOW_NAME.equals(className)) {
      return classfileBuffer;
    }

    System.out.println("Transforming Window...");
    ClassReader reader = new ClassReader(classfileBuffer);
    ClassWriter defaultWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    ClassVisitor visitor = new WindowVisitor(defaultWriter);

    try {
      reader.accept(visitor, ClassReader.EXPAND_FRAMES);
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return defaultWriter.toByteArray();
  }

  private static class WindowVisitor extends ClassVisitor {
    private boolean myIsWindow = false;

    public WindowVisitor(ClassVisitor visitor) {
      super(Opcodes.ASM5, visitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      if (WINDOW_NAME.equals(name)) {
        myIsWindow = true;
      }
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name,
                                     final String desc, final String signature, final String[] exceptions) {
      MethodVisitor defaultVisitor = super.visitMethod(access, name, desc, signature, exceptions);
      if (!myIsWindow || defaultVisitor == null) {
        return defaultVisitor;
      }

      if ("paint".equals(name) && "(Ljava/awt/Graphics;)V".equals(desc)) {
        return new PaintMethodVisitor(defaultVisitor, access, name, desc);
      }

      return defaultVisitor;
    }
  }

  private static class PaintMethodVisitor extends GeneratorAdapter {
    private final Type myWindowPaintMethodStatType = Type.getType(WindowPaintMethodStat.class);
    private int myWindowPaintMethodStatIndex = -1;

    public PaintMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
      super(Opcodes.ASM5, mv, access, name, desc);
      System.out.println("\t...instrumenting " + name);
    }

    @Override
    public void visitCode() {
      super.visitCode();
      newInstance(myWindowPaintMethodStatType);
      dup();
      loadThis(); // Prepare the stack for the call to the constructor of WindowPaintMethodStat.
      invokeConstructor(myWindowPaintMethodStatType, Method.getMethod(WindowPaintMethodStat.class.getConstructors()[0]));
      myWindowPaintMethodStatIndex = newLocal(myWindowPaintMethodStatType);
      storeLocal(myWindowPaintMethodStatIndex);
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
          assert myWindowPaintMethodStatIndex > 0;
          loadLocal(myWindowPaintMethodStatIndex); // Load the PaintChildrenMethodStat local variable onto the stack (it takes no parameters).
          invokeVirtual(myWindowPaintMethodStatType, new Method("endMethod", "()V"));
          break;

        default:
          // Do nothing special.
          break;
      }
      super.visitInsn(opcode);
    }
  }
}

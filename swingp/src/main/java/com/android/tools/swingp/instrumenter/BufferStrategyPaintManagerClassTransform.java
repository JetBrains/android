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

import com.android.tools.swingp.BufferStrategyPaintMethodStat;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

public class BufferStrategyPaintManagerClassTransform implements ClassFileTransformer {
  private static final String BUFFER_STRATEGY_PAINT_MANAGER_NAME = "javax/swing/BufferStrategyPaintManager";

  @Override
  public byte[] transform(ClassLoader loader,
                          String className,
                          Class<?> classBeingRedefined,
                          ProtectionDomain protectionDomain,
                          byte[] classfileBuffer) throws IllegalClassFormatException {
    if (!BUFFER_STRATEGY_PAINT_MANAGER_NAME.equals(className)) {
      return classfileBuffer;
    }

    System.out.println("Transforming BufferStrategyPaintManager...");
    ClassReader reader = new ClassReader(classfileBuffer);
    ClassWriter defaultWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    ClassVisitor visitor = new BufferStrategyPaintManagerVisitor(defaultWriter);

    try {
      reader.accept(visitor, ClassReader.EXPAND_FRAMES);
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return defaultWriter.toByteArray();
  }

  private static class BufferStrategyPaintManagerVisitor extends ClassVisitor {
    private boolean myIsBufferStrategyPaintManager = false;

    public BufferStrategyPaintManagerVisitor(ClassVisitor visitor) {
      super(Opcodes.ASM5, visitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      if (BUFFER_STRATEGY_PAINT_MANAGER_NAME.equals(name)) {
        myIsBufferStrategyPaintManager = true;
      }
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name,
                                     final String desc, final String signature, final String[] exceptions) {
      MethodVisitor defaultVisitor = super.visitMethod(access, name, desc, signature, exceptions);
      if (!myIsBufferStrategyPaintManager || defaultVisitor == null) {
        return defaultVisitor;
      }

      if ("paint".equals(name) && "(Ljavax/swing/JComponent;Ljavax/swing/JComponent;Ljava/awt/Graphics;IIII)Z".equals(desc)) {
        return new PaintMethodVisitor(defaultVisitor, access, name, desc);
      }

      return defaultVisitor;
    }
  }

  private static class PaintMethodVisitor extends GeneratorAdapter {
    private final Type myBufferStrategyPaintMethodStatType = Type.getType(BufferStrategyPaintMethodStat.class);

    public PaintMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
      super(Opcodes.ASM5, mv, access, name, desc);
      System.out.println("\t...instrumenting " + name);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
      if ((opcode == Opcodes.INVOKEVIRTUAL && !isInterface && "javax/swing/JComponent".equals(owner) && name.equals("paintToOffscreen")) ||
          (opcode == Opcodes.INVOKESPECIAL && !isInterface && name.equals("paint"))) {
        addMethodStat(opcode, owner, name, descriptor);
      }
      else {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }
    }

    private void addMethodStat(int opcode, String owner, String name, String descriptor) {
      newInstance(myBufferStrategyPaintMethodStatType);
      dup();
      loadThis(); // Prepare the stack for the call to the constructor of BufferStrategyPaintManagerStat.
      push(opcode == Opcodes.INVOKEVIRTUAL);
      invokeConstructor(myBufferStrategyPaintMethodStatType, Method.getMethod(BufferStrategyPaintMethodStat.class.getConstructors()[0]));
      int index = newLocal(myBufferStrategyPaintMethodStatType);
      storeLocal(index);

      super.visitMethodInsn(opcode, owner, name, descriptor, false);

      loadLocal(index); // Load the BufferStrategyPaintMethodStat local variable onto the stack (it takes no parameters).
      invokeVirtual(myBufferStrategyPaintMethodStatType, new Method("endMethod", "()V"));
    }
  }
}

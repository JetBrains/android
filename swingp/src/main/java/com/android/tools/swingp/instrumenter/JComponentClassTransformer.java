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

import com.android.tools.swingp.PaintChildrenMethodStat;
import com.android.tools.swingp.PaintComponentMethodStat;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import javax.swing.JComponent;
import org.jetbrains.org.objectweb.asm.AnnotationVisitor;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.Handle;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.TypePath;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

public class JComponentClassTransformer implements ClassFileTransformer {
  private static final String JCOMPONENT_NAME = JComponent.class.getCanonicalName().replace('.', '/');

  @Override
  public byte[] transform(ClassLoader loader,
                          String className,
                          Class<?> classBeingRedefined,
                          ProtectionDomain protectionDomain,
                          byte[] classfileBuffer) {
    if (!JCOMPONENT_NAME.equals(className)) {
      return classfileBuffer;
    }

    System.out.println("Transforming JComponent...");
    ClassReader reader = new ClassReader(classfileBuffer);
    ClassWriter defaultWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    ClassVisitor visitor = new JComponentVisitor(defaultWriter);

    try {
      reader.accept(visitor, ClassReader.EXPAND_FRAMES);
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return defaultWriter.toByteArray();
  }

  private static class JComponentVisitor extends ClassVisitor {
    private boolean myIsJComponent = false;

    public JComponentVisitor(ClassVisitor visitor) {
      super(Opcodes.ASM5, visitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      if (JCOMPONENT_NAME.equals(name)) {
        myIsJComponent = true;
      }
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name,
                                     final String desc, final String signature, final String[] exceptions) {
      MethodVisitor defaultVisitor = super.visitMethod(access, name, desc, signature, exceptions);
      if (!myIsJComponent || defaultVisitor == null) {
        return defaultVisitor;
      }

      if ("paintToOffscreen".equals(name) && "(Ljava/awt/Graphics;IIIIII)V".equals(desc) ||
          "paint".equals(name) && "(Ljava/awt/Graphics;)V".equals(desc)) {
        return new JComponentPaintMethodVisitor(defaultVisitor, access, name, desc);
      }

      return defaultVisitor;
    }
  }

  private static class JComponentPaintMethodVisitor extends GeneratorAdapter {
    private static final String PAINT_DESCRIPTOR = "(Ljava/awt/Graphics;)V";
    private static final String GRAPHICS2D_NAME = Graphics2D.class.getCanonicalName().replace('.', '/');
    private static final String PAINT_CHILDREN_METHOD_STAT_NAME = PaintChildrenMethodStat.class.getCanonicalName().replace('.', '/');
    private static final String PAINT_COMPONENT_METHOD_STAT_NAME = PaintComponentMethodStat.class.getCanonicalName().replace('.', '/');

    private final Type paintChildrenMethodStatType = Type.getType(PaintChildrenMethodStat.class);

    private int myPaintChildrenMethodStatLocalIndex = -1;
    private int myPaintComponentMethodStatLocalIndex = -1;
    private int myTransformLocalIndex = -1;
    private CaptureClipStateMachine myClipStateMachine = CaptureClipStateMachine.NOT_MATCHED;
    private int myXIdx, myYIdx, myWIdx, myHIdx;

    public JComponentPaintMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
      super(Opcodes.ASM5, mv, access, name, desc);
      System.out.println("\t...instrumenting " + name);
    }

    @Override
    public void visitCode() {
      super.visitCode();

      // Generate code to store the current transform of Graphics2D.
      myTransformLocalIndex = newLocal(Type.getType(AffineTransform.class));
      super.visitVarInsn(Opcodes.ALOAD, 1); // Load the Graphics parameter.
      checkCast(Type.getType(Graphics2D.class));
      super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GRAPHICS2D_NAME, "getTransform", "()Ljava/awt/geom/AffineTransform;", false);
      storeLocal(myTransformLocalIndex);
    }

    /**
     * We need to find a particular sequence of instructions to capture the local indices of where those variables are stored in registers.
     * In particular, we need to find the exact contiguous sequence of [ALOAD, ILOAD, ILOAD, ILOAD, ILOAD] instructions.
     * Therefore, we construct a very simple and explicit state machine that encodes the discovery of this sequence of instructions.
     */
    @Override
    public void visitVarInsn(int opcode, int var) {
      switch (myClipStateMachine) {
        case INVOKEVIRTUAL:
          // Done, we've captured all the indices. Just keep skipping.
          break;
        case NOT_MATCHED:
          if (opcode == Opcodes.ALOAD && var == 0) {
            myClipStateMachine = CaptureClipStateMachine.ILOAD0;
          }
          break;
        case ILOAD0:
          if (opcode == Opcodes.ILOAD) {
            myXIdx = var;
            myClipStateMachine = CaptureClipStateMachine.ILOAD1;
          }
          break;
        case ILOAD1:
          if (opcode == Opcodes.ILOAD) {
            myYIdx = var;
            myClipStateMachine = CaptureClipStateMachine.ILOAD2;
          }
          break;
        case ILOAD2:
          if (opcode == Opcodes.ILOAD) {
            myWIdx = var;
            myClipStateMachine = CaptureClipStateMachine.ILOAD3;
          }
          break;
        case ILOAD3:
          if (opcode == Opcodes.ILOAD) {
            myHIdx = var;
            myClipStateMachine = CaptureClipStateMachine.INVOKEVIRTUAL;
          }
          break;
        default:
          myClipStateMachine = CaptureClipStateMachine.NOT_MATCHED;
          break;
      }
      super.visitVarInsn(opcode, var);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
      if (opcode == Opcodes.INVOKEVIRTUAL && !isInterface) {
        handleVirtualMethod(opcode, owner, name, descriptor);
      }
      else {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }
    }

    private void handleVirtualMethod(int opcode, String owner, String name, String descriptor) {
      handleClipStateMachineForVisitingIntermediateInstruction();

      // The calls to paintComponent should always be followed by paintBorder, whereas the calls to paintChildren are always by themselves.
      // Therefore, we enter prior to paintComponent/paintChildren, and exit after paintBorder/paintChildren.
      if (PAINT_DESCRIPTOR.equals(descriptor)) {
        if ("paintComponent".equals(name) && myClipStateMachine == CaptureClipStateMachine.INVOKEVIRTUAL) {
          // At this moment in the instruction stream, the "co" Graphics object is at the top of the stack. Dupe it and store it off.
          dup();
          int graphicsLocalIndex = newLocal(Type.getType(Graphics.class));
          storeLocal(graphicsLocalIndex);

          // Generate code to store the bounds.
          Type paintComponentMethodStatType = Type.getType(PaintComponentMethodStat.class);
          myPaintComponentMethodStatLocalIndex = newLocal(paintComponentMethodStatType);
          newInstance(paintComponentMethodStatType);
          dup(); // Duplicate the newly allocated object.

          loadThis(); // Load "this" onto the stack (we need it as the parameter).
          assert graphicsLocalIndex > 0;
          loadLocal(graphicsLocalIndex);
          assert myTransformLocalIndex > 0;
          loadLocal(myTransformLocalIndex);
          super.visitVarInsn(Opcodes.ILOAD, myXIdx);
          super.visitVarInsn(Opcodes.ILOAD, myYIdx);
          super.visitVarInsn(Opcodes.ILOAD, myWIdx);
          super.visitVarInsn(Opcodes.ILOAD, myHIdx);
          // The constructor will consume the top five items on the stack, which is the duplicated "this" reference.
          invokeConstructor(paintComponentMethodStatType, Method.getMethod(PaintComponentMethodStat.class.getConstructors()[0]));
          storeLocal(myPaintComponentMethodStatLocalIndex); // Store the initialized object.
        }
        else if ("paintChildren".equals(name)) {
          Type methodStatType = Type.getType(PaintChildrenMethodStat.class);
          myPaintChildrenMethodStatLocalIndex = newLocal(methodStatType);
          newInstance(methodStatType);
          dup(); // Duplicate the newly allocated object.
          loadThis(); // Load "this" onto the stack (we need it as the parameter).
          assert myTransformLocalIndex > 0;
          loadLocal(myTransformLocalIndex);
          // The constructor will consume the top two items on the stack, which is the duplicated "this" reference.
          invokeConstructor(paintChildrenMethodStatType, Method.getMethod(PaintChildrenMethodStat.class.getConstructors()[0]));
          storeLocal(myPaintChildrenMethodStatLocalIndex); // Store the initialized object.
        }
      }

      super.visitMethodInsn(opcode, owner, name, descriptor, false);

      if ((myPaintComponentMethodStatLocalIndex != -1 || myPaintChildrenMethodStatLocalIndex != -1) &&
          !("paintBorder".equals(name) || "paintChildren".equals(name) || "paintComponent".equals(name))) {
        System.out.println("Unexpected method call when waiting for end of paintBorder/paintChildren");
        assert false;
      }
      if (PAINT_DESCRIPTOR.equals(descriptor)) {
        if (myPaintComponentMethodStatLocalIndex != -1 &&
            "paintBorder".equals(name) &&
            myClipStateMachine == CaptureClipStateMachine.INVOKEVIRTUAL) {
          // Load the PaintComponentMethodStat local variable onto the stack (it takes no parameters).
          loadLocal(myPaintComponentMethodStatLocalIndex);
          super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PAINT_COMPONENT_METHOD_STAT_NAME, "endMethod", "()V", false);
          myPaintComponentMethodStatLocalIndex = -1;
        }
        else if (myPaintChildrenMethodStatLocalIndex != -1 && "paintChildren".equals(name)) {
          loadLocal(myPaintChildrenMethodStatLocalIndex); // Load the PaintChildrenMethodStat local variable onto the stack (it takes no parameters).
          super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PAINT_CHILDREN_METHOD_STAT_NAME, "endMethod", "()V", false);
          myPaintChildrenMethodStatLocalIndex = -1;
        }
      }
    }

    // Below overrides are just to reset the clip state machine.
    @Override
    public void visitInsn(int opcode) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitInsn(opcode);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitFieldInsn(opcode, owner, name, desc);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitMethodInsn(opcode, owner, name, desc);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitLabel(Label label) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitLabel(label);
    }

    @Override
    public void visitLdcInsn(Object cst) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitLdcInsn(cst);
    }

    @Override
    public void visitIincInsn(int var, int increment) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitIincInsn(var, increment);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitTableSwitchInsn(min, max, dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitLookupSwitchInsn(dflt, keys, labels);
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitMultiANewArrayInsn(desc, dims);
    }

    @Override
    public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      return super.visitInsnAnnotation(typeRef, typePath, desc, visible);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitTryCatchBlock(start, end, handler, type);
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      return super.visitTryCatchAnnotation(typeRef, typePath, desc, visible);
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      super.visitLocalVariable(name, desc, signature, start, end, index);
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef,
                                                          TypePath typePath,
                                                          Label[] start,
                                                          Label[] end,
                                                          int[] index,
                                                          String desc,
                                                          boolean visible) {
      handleClipStateMachineForVisitingIntermediateInstruction();
      return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, desc, visible);
    }

    /**
     * Determines if the clip state machine as encoded in {@code #visitVarInsn(int,int)} needs to be set back to the starting state,
     * depending on if we've encoutered an instruction not in the desired sequence in the middle of the search.
     */
    private void handleClipStateMachineForVisitingIntermediateInstruction() {
      if (myClipStateMachine != CaptureClipStateMachine.INVOKEVIRTUAL) {
        // The clip state machine are consecutive instructions.
        myClipStateMachine = CaptureClipStateMachine.NOT_MATCHED;
      }
    }

    private enum CaptureClipStateMachine {
      NOT_MATCHED,
      ILOAD0,
      ILOAD1,
      ILOAD2,
      ILOAD3,
      INVOKEVIRTUAL
    }
  }
}

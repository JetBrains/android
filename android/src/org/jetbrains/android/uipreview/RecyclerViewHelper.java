/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.uipreview;

import org.jetbrains.org.objectweb.asm.*;

import static org.jetbrains.org.objectweb.asm.Opcodes.*;

/**
 * Some constants that are needed to render the RecyclerView correctly.
 */
@SuppressWarnings("SpellCheckingInspection")
public class RecyclerViewHelper {

  public static final String PACKAGE_NAME = "com.android.layoutlib.bridge.android.support";
  public static final String CN_CUSTOM_ADAPTER = PACKAGE_NAME + ".Adapter";
  public static final String CN_CUSTOM_VIEW_HOLDER = PACKAGE_NAME + ".Adapter$ViewHolder";
  public static final String CN_RV_ADAPTER = "android.support.v7.widget.RecyclerView$Adapter";
  public static final String CN_RV_LAYOUT_MANAGER = "android.support.v7.widget.RecyclerView$LayoutManager";

  // Lazily initialized.
  private static byte[] ourAdapterClass;
  private static byte[] ourViewHolder;

  public static byte[] getAdapterClass() {
    if (ourAdapterClass == null) {
      ourAdapterClass = getAdapterClassDump();
    }
    return ourAdapterClass;
  }

  public static byte[] getViewHolder() {
    if (ourViewHolder == null) {
      ourViewHolder = getViewHolderDump();
    }
    return ourViewHolder;
  }

  // See comment at the end of the file for how this method was generated.
  @SuppressWarnings("unused")  // Generated code.
  private static byte[] getAdapterClassDump() {
    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "com/android/layoutlib/bridge/android/support/Adapter",
             "Landroid/support/v7/widget/RecyclerView$Adapter<Landroid/support/v7/widget/RecyclerView$ViewHolder;>;",
             "android/support/v7/widget/RecyclerView$Adapter", null);

    cw.visitInnerClass("com/android/layoutlib/bridge/android/support/Adapter$ViewHolder",
                       "com/android/layoutlib/bridge/android/support/Adapter", "ViewHolder", ACC_PRIVATE + ACC_STATIC);

    cw.visitInnerClass("android/support/v7/widget/RecyclerView$ViewHolder", "android/support/v7/widget/RecyclerView", "ViewHolder",
                       ACC_PUBLIC + ACC_STATIC + ACC_ABSTRACT);

    cw.visitInnerClass("android/support/v7/widget/RecyclerView$Adapter", "android/support/v7/widget/RecyclerView", "Adapter",
                       ACC_PUBLIC + ACC_STATIC + ACC_ABSTRACT);

    {
      fv = cw.visitField(ACC_PRIVATE + ACC_FINAL + ACC_STATIC, "ITEM_COUNT", "I", null, new Integer(10));
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_PRIVATE, "mId", "I", null, null);
      fv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "android/support/v7/widget/RecyclerView$Adapter", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv =
        cw.visitMethod(ACC_PUBLIC, "onCreateViewHolder", "(Landroid/view/ViewGroup;I)Landroid/support/v7/widget/RecyclerView$ViewHolder;",
                       null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, "com/android/layoutlib/bridge/android/support/Adapter", "mId", "I");
      Label l0 = new Label();
      mv.visitJumpInsn(IFLE, l0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "android/view/ViewGroup", "getContext", "()Landroid/content/Context;", false);
      mv.visitMethodInsn(INVOKESTATIC, "android/view/LayoutInflater", "from", "(Landroid/content/Context;)Landroid/view/LayoutInflater;",
                         false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, "com/android/layoutlib/bridge/android/support/Adapter", "mId", "I");
      mv.visitVarInsn(ALOAD, 1);
      mv.visitInsn(ICONST_0);
      mv.visitMethodInsn(INVOKEVIRTUAL, "android/view/LayoutInflater", "inflate", "(ILandroid/view/ViewGroup;Z)Landroid/view/View;", false);
      mv.visitVarInsn(ASTORE, 3);
      Label l1 = new Label();
      mv.visitJumpInsn(GOTO, l1);
      mv.visitLabel(l0);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitTypeInsn(NEW, "android/widget/TextView");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "android/view/ViewGroup", "getContext", "()Landroid/content/Context;", false);
      mv.visitMethodInsn(INVOKESPECIAL, "android/widget/TextView", "<init>", "(Landroid/content/Context;)V", false);
      mv.visitVarInsn(ASTORE, 3);
      mv.visitLabel(l1);
      mv.visitFrame(Opcodes.F_APPEND,1, new Object[] {"android/view/View"}, 0, null);
      mv.visitTypeInsn(NEW, "com/android/layoutlib/bridge/android/support/Adapter$ViewHolder");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitMethodInsn(INVOKESPECIAL, "com/android/layoutlib/bridge/android/support/Adapter$ViewHolder", "<init>",
                         "(Landroid/view/View;)V", false);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(4, 4);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "onBindViewHolder", "(Landroid/support/v7/widget/RecyclerView$ViewHolder;I)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 1);
      mv.visitFieldInsn(GETFIELD, "android/support/v7/widget/RecyclerView$ViewHolder", "itemView", "Landroid/view/View;");
      mv.visitVarInsn(ASTORE, 3);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitTypeInsn(INSTANCEOF, "android/widget/TextView");
      Label l0 = new Label();
      mv.visitJumpInsn(IFEQ, l0);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitTypeInsn(CHECKCAST, "android/widget/TextView");
      mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      mv.visitLdcInsn("Item number ");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
      mv.visitVarInsn(ILOAD, 2);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "setText", "(Ljava/lang/CharSequence;)V", false);
      mv.visitLabel(l0);
      mv.visitFrame(Opcodes.F_APPEND, 1, new Object[]{"android/view/View"}, 0, null);
      mv.visitInsn(RETURN);
      mv.visitMaxs(3, 4);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "getItemCount", "()I", null, null);
      mv.visitCode();
      mv.visitIntInsn(BIPUSH, 10);
      mv.visitInsn(IRETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "setLayoutId", "(I)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ILOAD, 1);
      mv.visitFieldInsn(PUTFIELD, "com/android/layoutlib/bridge/android/support/Adapter", "mId", "I");
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 2);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  // See comment at the end of the file for how this method was generated.
  @SuppressWarnings("unused")  // Generated code.
  private static byte[] getViewHolderDump() {
    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    cw.visit(V1_6, ACC_SUPER, "com/android/layoutlib/bridge/android/support/Adapter$ViewHolder", null,
             "android/support/v7/widget/RecyclerView$ViewHolder", null);

    cw.visitInnerClass("com/android/layoutlib/bridge/android/support/Adapter$ViewHolder",
                       "com/android/layoutlib/bridge/android/support/Adapter", "ViewHolder", ACC_PRIVATE + ACC_STATIC);

    cw.visitInnerClass("android/support/v7/widget/RecyclerView$ViewHolder", "android/support/v7/widget/RecyclerView", "ViewHolder",
                       ACC_PUBLIC + ACC_STATIC + ACC_ABSTRACT);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(Landroid/view/View;)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitMethodInsn(INVOKESPECIAL, "android/support/v7/widget/RecyclerView$ViewHolder", "<init>", "(Landroid/view/View;)V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 2);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }


  // The above dump methods were obtained by compiling the following class and running ASMifier on both Adapter and Adapter$ViewHolder.
  // $ echo com.android.layoutlib.bridge.android.support.Adapter com.android.layoutlib.bridge.android.support.Adapter\$ViewHolder \
  //       | xargs -n 1 java -classpath asm-debug-all-5.0.2.jar:. org.objectweb.asm.util.ASMifier
  //
  //package com.android.layoutlib.bridge.android.support;
  //
  //import android.support.v7.widget.RecyclerView;
  //import android.view.View;
  //import android.view.ViewGroup;
  //import android.widget.TextView;
  //
  //public class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  //
  //  private static final int ITEM_COUNT = 10;
  //  private int mId;
  //
  //  @Override
  //  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,
  //                                                    int viewType) {
  //    View view;
  //    if (mId > 0) {
  //      view = LayoutInflater.from(parent.getContext()).inflate(mId, parent, false);
  //    } else {
  //      view = new TextView(parent.getContext());
  //    }
  //    return new ViewHolder(view);
  //  }
  //
  //  @Override
  //  public void onBindViewHolder(RecyclerView.ViewHolder holder,
  //                               int position) {
  //    View view = holder.itemView;
  //    if (view instanceof TextView) {
  //      ((TextView) view).setText("Item number " + position);
  //    }
  //  }
  //
  //  @Override
  //  public int getItemCount() {
  //    return ITEM_COUNT;
  //  }
  //
  //  public void setLayoutId(int id) {
  //    mId = id;
  //  }
  //
  //  private static class ViewHolder extends RecyclerView.ViewHolder {
  //    public ViewHolder(View itemView) {
  //      super(itemView);
  //    }
  //  }
  //}

}

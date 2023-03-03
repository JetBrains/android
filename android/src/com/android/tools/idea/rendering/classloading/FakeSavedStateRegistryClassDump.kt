/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.rendering.classloading

import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.FieldVisitor
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Opcodes.*

/**
 * Dump to generate the bytecode to create the [FakeSavedStateRegistry] class.
 * [FakeSavedStateRegistry] class creates a fake [LifecycleOwner] used in case the layout editor
 * contains an implementation of a class that returns a null lifecycle owner (e.g. AbstractComposeView implementations).
 *
 * see also the comments below to see how this dump code is generated.
 */
object FakeSavedStateRegistryClassDump : Opcodes {

  val lifecycleClassDump: ByteArray = run {
    val classWriter = ClassWriter(0)
    var fieldVisitor: FieldVisitor
    var methodVisitor: MethodVisitor
    var annotationVisitor0: AnnotationVisitor
    classWriter.visit(
      V1_8,
      ACC_PUBLIC or ACC_SUPER,
      FAKE_SAVEDSTATE_REGISTRY_PATH,
      null,
      "java/lang/Object",
      arrayOf("androidx/savedstate/SavedStateRegistryOwner")
    )
    classWriter.visitSource("ViewTreeLifeSavedStateRegistryOwner.java", null)
    classWriter.visitInnerClass(
      "androidx/lifecycle/Lifecycle\$State",
      "androidx/lifecycle/Lifecycle",
      "State",
      ACC_PUBLIC or ACC_FINAL or ACC_STATIC or ACC_ENUM
    )
    run {
      fieldVisitor = classWriter.visitField(
        ACC_PRIVATE or ACC_FINAL,
        "registry",
        "Landroidx/lifecycle/LifecycleRegistry;",
        null,
        null
      )
      fieldVisitor.visitEnd()
    }
    run {
      fieldVisitor = classWriter.visitField(
        ACC_PRIVATE or ACC_FINAL,
        "controller",
        "Landroidx/savedstate/SavedStateRegistryController;",
        null,
        null
      )
      fieldVisitor.visitEnd()
    }
    run {
      methodVisitor = classWriter.visitMethod(
        ACC_PUBLIC,
        "<init>",
        "()V",
        null,
        null
      )
      methodVisitor.visitCode()
      val label0 = Label()
      methodVisitor.visitLabel(label0)
      methodVisitor.visitLineNumber(16, label0)
      methodVisitor.visitVarInsn(ALOAD, 0)
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
      val label1 = Label()
      methodVisitor.visitLabel(label1)
      methodVisitor.visitLineNumber(13, label1)
      methodVisitor.visitVarInsn(ALOAD, 0)
      methodVisitor.visitVarInsn(ALOAD, 0)
      methodVisitor.visitMethodInsn(
        INVOKESTATIC,
        "androidx/lifecycle/LifecycleRegistry",
        "createUnsafe",
        "(Landroidx/lifecycle/LifecycleOwner;)Landroidx/lifecycle/LifecycleRegistry;",
        false
      )
      methodVisitor.visitFieldInsn(PUTFIELD, FAKE_SAVEDSTATE_REGISTRY_PATH, "registry",
                                   "Landroidx/lifecycle/LifecycleRegistry;")
      val label2 = Label()
      methodVisitor.visitLabel(label2)
      methodVisitor.visitLineNumber(14, label2)
      methodVisitor.visitVarInsn(ALOAD, 0)
      methodVisitor.visitVarInsn(ALOAD, 0)
      methodVisitor.visitMethodInsn(
        INVOKESTATIC,
        "androidx/savedstate/SavedStateRegistryController",
        "create",
        "(Landroidx/savedstate/SavedStateRegistryOwner;)Landroidx/savedstate/SavedStateRegistryController;",
        false
      )
      methodVisitor.visitFieldInsn(
        PUTFIELD,
        FAKE_SAVEDSTATE_REGISTRY_PATH,
        "controller",
        "Landroidx/savedstate/SavedStateRegistryController;"
      )
      val label3 = Label()
      methodVisitor.visitLabel(label3)
      methodVisitor.visitLineNumber(17, label3)
      methodVisitor.visitVarInsn(ALOAD, 0)
      methodVisitor.visitFieldInsn(
        GETFIELD,
        FAKE_SAVEDSTATE_REGISTRY_PATH,
        "controller",
        "Landroidx/savedstate/SavedStateRegistryController;"
      )
      methodVisitor.visitTypeInsn(NEW, "android/os/Bundle")
      methodVisitor.visitInsn(DUP)
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "androidx/savedstate/SavedStateRegistryController", "performRestore",
                                    "(Landroid/os/Bundle;)V", false)
      val label4 = Label()
      methodVisitor.visitLabel(label4)
      methodVisitor.visitLineNumber(18, label4)
      methodVisitor.visitVarInsn(ALOAD, 0)
      methodVisitor.visitFieldInsn(
        GETFIELD,
        FAKE_SAVEDSTATE_REGISTRY_PATH,
        "registry",
        "Landroidx/lifecycle/LifecycleRegistry;"
      )
      methodVisitor.visitFieldInsn(
        GETSTATIC,
        "androidx/lifecycle/Lifecycle\$State",
        "RESUMED",
        "Landroidx/lifecycle/Lifecycle\$State;"
      )
      methodVisitor.visitMethodInsn(
        INVOKEVIRTUAL,
        "androidx/lifecycle/LifecycleRegistry",
        "setCurrentState",
        "(Landroidx/lifecycle/Lifecycle\$State;)V",
        false
      )
      val label5 = Label()
      methodVisitor.visitLabel(label5)
      methodVisitor.visitLineNumber(19, label5)
      methodVisitor.visitInsn(RETURN)
      val label6 = Label()
      methodVisitor.visitLabel(label6)
      methodVisitor.visitLocalVariable(
        "this",
        "L$FAKE_SAVEDSTATE_REGISTRY_PATH;",
        null,
        label0,
        label6,
        0
      )
      methodVisitor.visitMaxs(3, 1)
      methodVisitor.visitEnd()
    }
    run {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getSavedStateRegistry", "()Landroidx/savedstate/SavedStateRegistry;", null, null)
      run {
        annotationVisitor0 = methodVisitor.visitAnnotation("Landroidx/annotation/NonNull;", false)
        annotationVisitor0.visitEnd()
      }
      methodVisitor.visitCode()
      val label0 = Label()
      methodVisitor.visitLabel(label0)
      methodVisitor.visitLineNumber(24, label0)
      methodVisitor.visitVarInsn(ALOAD, 0)
      methodVisitor.visitFieldInsn(
        GETFIELD,
        FAKE_SAVEDSTATE_REGISTRY_PATH,
        "controller",
        "Landroidx/savedstate/SavedStateRegistryController;"
      )
      methodVisitor.visitMethodInsn(
        INVOKEVIRTUAL,
        "androidx/savedstate/SavedStateRegistryController",
        "getSavedStateRegistry",
        "()Landroidx/savedstate/SavedStateRegistry;",
        false
      )
      methodVisitor.visitInsn(ARETURN)
      val label1 = Label()
      methodVisitor.visitLabel(label1)
      methodVisitor.visitLocalVariable("this", "L$FAKE_SAVEDSTATE_REGISTRY_PATH;", null, label0, label1,
                                       0)
      methodVisitor.visitMaxs(1, 1)
      methodVisitor.visitEnd()
    }
    run {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getLifecycle", "()Landroidx/lifecycle/Lifecycle;", null, null)
      run {
        annotationVisitor0 = methodVisitor.visitAnnotation("Landroidx/annotation/NonNull;", false)
        annotationVisitor0.visitEnd()
      }
      methodVisitor.visitCode()
      val label0 = Label()
      methodVisitor.visitLabel(label0)
      methodVisitor.visitLineNumber(30, label0)
      methodVisitor.visitVarInsn(ALOAD, 0)
      methodVisitor.visitFieldInsn(
        GETFIELD,
        FAKE_SAVEDSTATE_REGISTRY_PATH,
        "registry",
        "Landroidx/lifecycle/LifecycleRegistry;"
      )
      methodVisitor.visitInsn(ARETURN)
      val label1 = Label()
      methodVisitor.visitLabel(label1)
      methodVisitor.visitLocalVariable(
        "this",
        "L$FAKE_SAVEDSTATE_REGISTRY_PATH;",
        null,
        label0,
        label1,
        0
      )
      methodVisitor.visitMaxs(1, 1)
      methodVisitor.visitEnd()
    }
    classWriter.visitEnd()
    classWriter.toByteArray()
  }
}


/*
The dump code above, is generated by using "ASMfier" from the following java code:

```
 package com.package.path.to.change;

 public class FakeSavedStateRegistry implements SavedStateRegistryOwner {
    private final LifecycleRegistry registry = LifecycleRegistry.createUnsafe(this);
    private final SavedStateRegistryController controller = SavedStateRegistryController.create(this);

    public FakeSavedStateRegistry() {
        controller.performRestore(new Bundle());
        registry.setCurrentState(Lifecycle.State.RESUMED);
    }

    @NonNull
    @Override
    public SavedStateRegistry getSavedStateRegistry() {
        return controller.getSavedStateRegistry();
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return registry;
    }
 }
```

1. Compile the java code written below.
2. Take the FakeSavedStateRegistry.class and run:
3. "echo ./FakeSavedStateRegistry.class | xargs -n 1 java jdk.internal.org.objectweb.asm.util.ASMifier > FakeSavedStateRegistryDump.java"
4. => "FakeSavedStateRegistryDump.java" contains the [FakeSavedStateRegistryClassDump] code

Note: using the package androidx/lifecycle/ looks common.
To avoid possible conflicts, we use an invalid package name.
For example, replace the path:
```
com/package/path/to/change
```
with
```
FAKE_SAVEDSTATE_REGISTRY_PATH
```
 */

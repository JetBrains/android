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


import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

private const val ORIGINAL_SUFFIX = "_Original"
const val FAKE_SAVEDSTATE_REGISTRY_PATH = "_layoutlib_/_internal_/androidx/lifecycle/FakeSavedStateRegistry"

/**
 * Transforms [androidx.lifecycle.ViewTreeLifecycleOwner].
 * Creates a new Lifecycle owner in case the getter returns a null [LifecycleOwner], otherwise we keep the behavior.
 * see also [FakeSavedStateRegistryClassDump] to see how the dependency [SavedStateRegistry] is generated
 */
class ViewTreeLifecycleTransform(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {

  private var isViewTreeLifecycleOwner = false
  override val uniqueId: String = ViewTreeLifecycleTransform::class.qualifiedName!!

  override fun visit(version: Int,
                     access: Int,
                     name: String?,
                     signature: String?,
                     superName: String?,
                     interfaces: Array<out String>?) {
    isViewTreeLifecycleOwner = name == "androidx/lifecycle/ViewTreeLifecycleOwner"
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitMethod(access: Int,
                           name: String?,
                           descriptor: String?,
                           signature: String?,
                           exceptions: Array<out String>?): MethodVisitor {
    if (!isViewTreeLifecycleOwner) {
      return super.visitMethod(access, name, descriptor, signature, exceptions)
    }
    if (name == "get") {
      // Wrap the original get() method to intercept its returned LifecycleOwner value.
      wrapGetMethod(super.visitMethod(access, name, descriptor, signature, exceptions))
      // Change the visibility of the original "get" method to private.
      val modifiedAccess = access and Opcodes.ACC_PUBLIC.inv() and Opcodes.ACC_PROTECTED.inv() or Opcodes.ACC_PRIVATE
      // Rename the get() method to get_Original()
      return super.visitMethod(modifiedAccess, "get$ORIGINAL_SUFFIX", descriptor, signature, exceptions)
    }
    return super.visitMethod(access, name, descriptor, signature, exceptions)
  }

  /**
   * Wrapper for the original get().
   * If the original get() (renamed to get_Original) returns null, the wrapper generates a new fake LifecycleOwner with [FakeSavedStateRegistry].
   *
   * See the comments below the code to check how the method wrapper generates the ASM code.
   */
  private fun wrapGetMethod(mv: MethodVisitor) {
    // LifecycleOwner owner = get_Original(view);
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitMethodInsn(
      Opcodes.INVOKESTATIC,
      "androidx/lifecycle/ViewTreeLifecycleOwner",
      "get$ORIGINAL_SUFFIX",
      "(Landroid/view/View;)Landroidx/lifecycle/LifecycleOwner;",
      false
    )
    mv.visitVarInsn(Opcodes.ASTORE, 1)
    // if (owner != null) {
    mv.visitVarInsn(Opcodes.ALOAD, 1)
    val label2 = Label()
    mv.visitJumpInsn(Opcodes.IFNULL, label2)
    // return owner;
    mv.visitVarInsn(Opcodes.ALOAD, 1)
    mv.visitInsn(Opcodes.ARETURN)
    //}else{
    mv.visitLabel(label2)
    mv.visitFrame(
      Opcodes.F_APPEND,
      1,
      arrayOf<Any>("androidx/lifecycle/LifecycleOwner"),
      0,
      null
    )
    // FakeSavedStateRegistry savedStateRegistryOwner = new FakeSavedStateRegistry();
    mv.visitTypeInsn(Opcodes.NEW, FAKE_SAVEDSTATE_REGISTRY_PATH)
    mv.visitInsn(Opcodes.DUP)
    mv.visitMethodInsn(
      Opcodes.INVOKESPECIAL,
      FAKE_SAVEDSTATE_REGISTRY_PATH,
      "<init>",
      "()V",
      false
    )
    mv.visitVarInsn(Opcodes.ASTORE, 2)
    val label4 = Label()
    mv.visitLabel(label4)
    // ViewTreeSavedStateRegistryOwner.set(view, savedStateRegistryOwner);
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitVarInsn(Opcodes.ALOAD, 2)
    mv.visitMethodInsn(
      Opcodes.INVOKESTATIC,
      "androidx/savedstate/ViewTreeSavedStateRegistryOwner",
      "set",
      "(Landroid/view/View;Landroidx/savedstate/SavedStateRegistryOwner;)V",
      false
    )
    // return savedStateRegistryOwner; }
    mv.visitVarInsn(Opcodes.ALOAD, 2)
    mv.visitInsn(Opcodes.ARETURN)

    //<--- ends to visit the method
    mv.visitMaxs(2, 3)
    mv.visitEnd()
  }
}

/*
Given the original androidx.lifecycle.ViewTreeLifecycleOwner:

```
public class ViewTreeLifecycleOwner {
    private ViewTreeLifecycleOwner() {
    }

    public static void set(@NonNull View view, @Nullable LifecycleOwner lifecycleOwner) {
        view.setTag(id.view_tree_lifecycle_owner, lifecycleOwner);
    }

    @Nullable
    public static LifecycleOwner get(@NonNull View view) {
        LifecycleOwner found = (LifecycleOwner)view.getTag(id.view_tree_lifecycle_owner);
        if (found != null) {
            return found;
        } else {
            View parentView;
            for(ViewParent parent = view.getParent(); found == null && parent instanceof View; parent = parentView.getParent()) {
                parentView = (View)parent;
                found = (LifecycleOwner)parentView.getTag(id.view_tree_lifecycle_owner);
            }

            return found;
        }
    }
}
```
Steps to wrap the get() method to check its returning value:
1. Renaming the get() method to get_Original().
2. Wrapping get_Original() with a new get().
3. Visit the new get() and intercepting get_Original() if it returns a null value.

Why bytecode manipulation?
As we don't have any access to androidx.lifecycle.ViewTreeLifecycleOwner,
what we can do to fix the null lifecycle in the preview is to intercept the value only if the user uses it.

```
public class ViewTreeLifecycleOwner {

    [...]

    // get() becomes get_Original()
    @Nullable
    public static LifecycleOwner get_Original(@NonNull View view) {
        LifecycleOwner found = (LifecycleOwner) view.getTag(id.view_tree_lifecycle_owner);
        if (found != null) {
            return found;
        } else {
            View parentView;
            for (ViewParent parent = view.getParent(); found == null && parent instanceof View; parent = parentView.getParent()) {
                parentView = (View) parent;
                found = (LifecycleOwner) parentView.getTag(id.view_tree_lifecycle_owner);
            }

            return found;
        }
    }

    // result of the wrapper
    public static LifecycleOwner get(@NonNull View view) {
        LifecycleOwner owner = get_Original(view);
        if (owner != null) {
            return owner ;
        } else {
            FakeSavedStateRegistry savedStateRegistryOwner = new FakeSavedStateRegistry();
            ViewTreeSavedStateRegistryOwner.set(view, savedStateRegistryOwner);
            return savedStateRegistryOwner;
        }
    }
}

```
The dump code above, is generated using the ASMfier

1. Compile the java code of the .
2. Take the ViewTreeLifecycleOwner.class and run:
3. "echo ./ViewTreeLifecycleOwner.class | xargs -n 1 java jdk.internal.org.objectweb.asm.util.ASMifier > ViewTreeLifecycleOwnerDump.java"
4. => "ViewTreeLifecycleOwner.java" contains the [ViewTreeLifecycleOwner] code

You don't need to copy the whole classVisitor as the code we need is the only related to the new get() method.
From the dump class you have just generated, copy the code that goes from:
```
mw.visitMethodInsn(
      Opcodes.INVOKESTATIC,
      "androidx/lifecycle/ViewTreeLifecycleOwner",
      "get",
      "(Landroid/view/View;)Landroidx/lifecycle/LifecycleOwner;",
      false
)
```
to:
```
mw.visitMaxs(2, 3)
mw.visitEnd()
```

Replace all the occurrences of FakeSavedStateRegistryClass with the const FAKE_SAVEDSTATE_REGISTRY_PATH
paste the code in a wrapper function eg: [wrapperForGetMethod].
 */



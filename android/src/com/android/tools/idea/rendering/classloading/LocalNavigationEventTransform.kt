/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.rendering.classloading.ClassVisitorUniqueIdProvider
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

private const val LOCAL_NAVIGATION_EVENT_OWNER_EVENT = "androidx/navigationevent/compose/LocalNavigationEventDispatcherOwner"

private const val INTERACTIVE_PREVIEW_BACK_NAVIGATION_UPDATER =
  "com/android/tools/idea/compose/preview/scene/InteractivePreviewBackNavigationUpdater"

private const val ORIGINAL_SUFFIX = "_Original"

/**
 * A [ClassVisitor] that transforms [LocalNavigationEventDispatcherOwner] to ensure a valid [NavigationEventDispatcherOwner] is returned in
 * the preview environment.
 *
 * This transform intercepts the `getCurrent` method of `LocalNavigationEventDispatcherOwner`. If the original method returns `null` (which
 * indicates that no dispatcher owner is provided in the current CompositionLocal), this transform returns a
 * `FakeNavigationEventDispatcherOwner` instead. This allows Compose previews to function correctly even without an explicit
 * `NavigationEventDispatcherOwner` provided.
 *
 * TODO(b/483631567): Remove this transform once we implement the permanent solution.
 *
 * Please check the documentation in the end of this class to understand how the content of this class can be properly regenerated if the
 * implementation of LocalNavigationEventDispatcherOwner changes significantly, to a point that the transformation doesn't work.
 */
class LocalNavigationEventTransform(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {
  private var isLocalNavigationEventTransformOwner = false
  override val uniqueId: String = LocalNavigationEventTransform::class.qualifiedName!!

  override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String?>?) {
    isLocalNavigationEventTransformOwner = name == LOCAL_NAVIGATION_EVENT_OWNER_EVENT
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitMethod(
    access: Int,
    name: String?,
    descriptor: String?,
    signature: String?,
    exceptions: Array<out String?>?,
  ): MethodVisitor? {
    if (!StudioFlags.COMPOSE_INTERACTIVE_PREVIEW_PREDICTIVE_BACK.get()) {
      return super.visitMethod(access, name, descriptor, signature, exceptions)
    }
    if (!isLocalNavigationEventTransformOwner) {
      return super.visitMethod(access, name, descriptor, signature, exceptions)
    }
    // The java field "current" is found as a "getCurrent" when visiting the method.
    if (name == "getCurrent") {
      // Wrap the original getCurrent
      wrapCurrentMethod(super.visitMethod(access, name, descriptor, signature, exceptions))
      // Change the visibility of the original "getCurrent" method to private.
      val modifiedAccess = access and Opcodes.ACC_PUBLIC.inv() and Opcodes.ACC_PROTECTED.inv() or Opcodes.ACC_PRIVATE
      // Rename the get() method to get_Original()
      return super.visitMethod(modifiedAccess, "getCurrent$ORIGINAL_SUFFIX", descriptor, signature, exceptions)
    }
    return super.visitMethod(access, name, descriptor, signature, exceptions)
  }

  /**
   * DO NOT CHANGE THIS FILE MANUALLY: you need to use ASMifier to generate the dump code. Go to go/how-to-ASMify-Navigation3-loaders for
   * more information.
   */
  fun wrapCurrentMethod(methodVisitor: MethodVisitor) =
    with(methodVisitor) {
      visitCode()
      val label0 = Label()
      visitLabel(label0)
      // Load composer
      visitVarInsn(Opcodes.ALOAD, 1)
      visitLdcInsn(-942026292)
      visitLdcInsn("C(<get-current>)48@2120L16:LocalNavigationEventDispatcherOwner.kt#wc8b4r")
      val label1 = Label()
      visitLabel(label1)
      visitLineNumber(49, label1)
      // Start source information marker
      visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "androidx/compose/runtime/ComposerKt",
        "sourceInformationMarkerStart",
        "(Landroidx/compose/runtime/Composer;ILjava/lang/String;)V",
        false,
      )
      // Check if trace is in progress
      visitMethodInsn(Opcodes.INVOKESTATIC, "androidx/compose/runtime/ComposerKt", "isTraceInProgress", "()Z", false)
      val label2 = Label()
      visitJumpInsn(Opcodes.IFEQ, label2)
      visitLdcInsn(-942026292)
      visitVarInsn(Opcodes.ILOAD, 2)
      visitInsn(Opcodes.ICONST_M1)
      visitLdcInsn(
        "androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner.<get-current> (LocalNavigationEventDispatcherOwner.kt:48)"
      )
      // Start trace event
      visitMethodInsn(Opcodes.INVOKESTATIC, "androidx/compose/runtime/ComposerKt", "traceEventStart", "(IIILjava/lang/String;)V", false)
      visitLabel(label2)
      visitFrame(Opcodes.F_SAME, 0, null, 0, null)
      visitVarInsn(Opcodes.ALOAD, 0)
      visitVarInsn(Opcodes.ALOAD, 1)
      visitIntInsn(Opcodes.BIPUSH, 14)
      visitVarInsn(Opcodes.ILOAD, 2)
      visitInsn(Opcodes.IAND)
      // Call the original getCurrent method (renamed)
      visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "androidx/navigationevent/compose/LocalNavigationEventDispatcherOwner",
        "getCurrent$ORIGINAL_SUFFIX",
        "(Landroidx/compose/runtime/Composer;I)Landroidx/navigationevent/NavigationEventDispatcherOwner;",
        false,
      )
      val label3 = Label()
      // If original current is not null, jump to label3 (return original)
      visitJumpInsn(Opcodes.IFNONNULL, label3)
      visitVarInsn(Opcodes.ALOAD, 1)
      visitLdcInsn(-588891712)
      // Start Compose replace group
      visitMethodInsn(Opcodes.INVOKEINTERFACE, "androidx/compose/runtime/Composer", "startReplaceGroup", "(I)V", true)
      val label4 = Label()
      visitLabel(label4)
      visitLineNumber(53, label4)
      visitVarInsn(Opcodes.ALOAD, 1)
      // End Compose replace group
      visitMethodInsn(Opcodes.INVOKEINTERFACE, "androidx/compose/runtime/Composer", "endReplaceGroup", "()V", true)
      // Initialize and set the fake dispatcher owner
      val label6 = setNavigationEventDispatcherOwner(methodVisitor)
      val label7 = Label()
      visitLabel(label7)
      visitLineNumber(52, label7)
      // Load the created owner
      visitVarInsn(Opcodes.ALOAD, 4)
      val label8 = Label()
      visitLabel(label8)
      // Cast to the expected return type
      visitTypeInsn(Opcodes.CHECKCAST, "androidx/navigationevent/NavigationEventDispatcherOwner")
      val label9 = Label()
      visitJumpInsn(Opcodes.GOTO, label9)
      visitLabel(label3)
      visitLineNumber(53, label3)
      visitFrame(Opcodes.F_SAME, 0, null, 0, null)
      visitVarInsn(Opcodes.ALOAD, 1)
      visitLdcInsn(-588711602)
      // Start Compose replace group
      visitMethodInsn(Opcodes.INVOKEINTERFACE, "androidx/compose/runtime/Composer", "startReplaceGroup", "(I)V", true)
      visitVarInsn(Opcodes.ALOAD, 1)
      visitLdcInsn("53@2345L16")
      // Emit source information
      visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "androidx/compose/runtime/ComposerKt",
        "sourceInformation",
        "(Landroidx/compose/runtime/Composer;Ljava/lang/String;)V",
        false,
      )
      val label10 = Label()
      visitLabel(label10)
      visitLineNumber(54, label10)
      visitVarInsn(Opcodes.ALOAD, 0)
      visitVarInsn(Opcodes.ALOAD, 1)
      visitIntInsn(Opcodes.BIPUSH, 14)
      visitVarInsn(Opcodes.ILOAD, 2)
      visitInsn(Opcodes.IAND)
      // Call the original getCurrent method (renamed)
      visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "androidx/navigationevent/compose/LocalNavigationEventDispatcherOwner",
        "getCurrent$ORIGINAL_SUFFIX",
        "(Landroidx/compose/runtime/Composer;I)Landroidx/navigationevent/NavigationEventDispatcherOwner;",
        false,
      )
      val label11 = Label()
      visitLabel(label11)
      visitLineNumber(53, label11)
      visitVarInsn(Opcodes.ASTORE, 4)
      visitVarInsn(Opcodes.ALOAD, 1)
      // End Compose replace group
      visitMethodInsn(Opcodes.INVOKEINTERFACE, "androidx/compose/runtime/Composer", "endReplaceGroup", "()V", true)
      visitVarInsn(Opcodes.ALOAD, 4)
      visitLabel(label9)
      visitLineNumber(49, label9)
      visitFrame(
        Opcodes.F_FULL,
        5,
        arrayOf<Any?>(
          "androidx/navigationevent/compose/LocalNavigationEventDispatcherOwner",
          "androidx/compose/runtime/Composer",
          Opcodes.INTEGER,
          Opcodes.TOP,
          "java/lang/Object",
        ),
        1,
        arrayOf<Any>("androidx/navigationevent/NavigationEventDispatcherOwner"),
      )
      visitVarInsn(Opcodes.ASTORE, 3)
      // Check if trace is in progress
      visitMethodInsn(Opcodes.INVOKESTATIC, "androidx/compose/runtime/ComposerKt", "isTraceInProgress", "()Z", false)
      val label12 = Label()
      visitJumpInsn(Opcodes.IFEQ, label12)
      // End trace event
      visitMethodInsn(Opcodes.INVOKESTATIC, "androidx/compose/runtime/ComposerKt", "traceEventEnd", "()V", false)
      visitLabel(label12)
      visitFrame(
        Opcodes.F_FULL,
        5,
        arrayOf<Any?>(
          "androidx/navigationevent/compose/LocalNavigationEventDispatcherOwner",
          "androidx/compose/runtime/Composer",
          Opcodes.INTEGER,
          "androidx/navigationevent/NavigationEventDispatcherOwner",
          "java/lang/Object",
        ),
        0,
        arrayOf<Any?>(),
      )
      visitVarInsn(Opcodes.ALOAD, 1)
      // End source information marker
      visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "androidx/compose/runtime/ComposerKt",
        "sourceInformationMarkerEnd",
        "(Landroidx/compose/runtime/Composer;)V",
        false,
      )
      visitVarInsn(Opcodes.ALOAD, 3)
      visitInsn(Opcodes.ARETURN)
      val label13 = Label()
      visitLabel(label13)
      visitLocalVariable("owner", "Landroidx/navigationevent/compose/FakeNavigationEventDispatcherOwner;", null, label6, label8, 4)
      visitLocalVariable("this", "Landroidx/navigationevent/compose/LocalNavigationEventDispatcherOwner;", null, label0, label13, 0)
      visitLocalVariable("\$composer", "Landroidx/compose/runtime/Composer;", null, label0, label13, 1)
      visitLocalVariable("\$changed", "I", null, label0, label13, 2)
      visitMaxs(4, 5)
      visitEnd()
    }

  /**
   * Generates the bytecode for setting the [NavigationEventDispatcherOwner] on the [InteractivePreviewBackNavigationUpdater].
   *
   * This method performs the following operations:
   * 1. Creates a new instance of `androidx.navigationevent.compose.FakeNavigationEventDispatcherOwner`.
   * 2. Initializes the instance.
   * 3. Stores the instance in a local variable.
   * 4. Retrieves the static instance of [InteractivePreviewBackNavigationUpdater].
   * 5. Calls [InteractivePreviewBackNavigationUpdater.setNavigationEventDispatcherOwner] with the created owner.
   *
   * @param methodVisitor The [MethodVisitor] to write the instructions to.
   * @return The [Label] marking the end of the initialization and setting process.
   */
  private fun setNavigationEventDispatcherOwner(methodVisitor: MethodVisitor): Label =
    with(methodVisitor) {
      val label5 = Label()
      visitLabel(label5)
      visitLineNumber(50, label5)
      // val owner = FakeNavigationEventDispatcherOwner()
      visitTypeInsn(Opcodes.NEW, "androidx/navigationevent/compose/FakeNavigationEventDispatcherOwner")
      visitInsn(Opcodes.DUP)
      visitMethodInsn(Opcodes.INVOKESPECIAL, "androidx/navigationevent/compose/FakeNavigationEventDispatcherOwner", "<init>", "()V", false)
      visitVarInsn(Opcodes.ASTORE, 4)
      val label6 = Label()
      visitLabel(label6)
      visitLineNumber(51, label6)
      // InteractivePreviewBackNavigationUpdater.setNavigationEventDispatcherOwner(owner)
      visitFieldInsn(
        Opcodes.GETSTATIC,
        INTERACTIVE_PREVIEW_BACK_NAVIGATION_UPDATER,
        "INSTANCE",
        "L$INTERACTIVE_PREVIEW_BACK_NAVIGATION_UPDATER;",
      )
      // owner
      visitVarInsn(Opcodes.ALOAD, 4)
      visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        INTERACTIVE_PREVIEW_BACK_NAVIGATION_UPDATER,
        "setNavigationEventDispatcherOwner",
        "(Ljava/lang/Object;)V",
        false,
      )
      return label6
    }
}

/*
Given the original LocalNavigationEventDispatcherOwner:

public object LocalNavigationEventDispatcherOwner {
    private val LocalNavigationEventDispatcherOwner =
        compositionLocalWithHostDefaultOf(NavigationEventDispatcherOwnerHostDefaultKey)

    public val current: NavigationEventDispatcherOwner?
        @Composable get() = LocalNavigationEventDispatcherOwner.current

   public infix fun provides(...)
}

We want to wrap the current (in java getCurrent()) method to check its returning value and return a dump instance of NavigationEventDispatcherOwner:
1) Rename the getCurrent() to getCurrent_Original().
2) Wrapping getCurrent_Original with a new getCurrent().
3) Visit the new getCurrent() and intercepting getCurrent_Original if it returns a null value.

Why bytecode manipulation?
NavigationEventDispatcherOwner is used in the navigationevent library, which is using compileSdk 36, and we can't yet bump ui-tooling library to compileSdk 36.
As a temporary solution we need to use bytecode manipulation to include the APIS of predictive back.

After the manipulation LocalNavigationEventDispatcherOwner should look like this:

public object LocalNavigationEventDispatcherOwner {

  private val LocalNavigationEventDispatcherOwner =
        compositionLocalWithHostDefaultOf(NavigationEventDispatcherOwnerHostDefaultKey)

  public val current_Original: NavigationEventDispatcherOwner?
    @Composable
    get() = LocalNavigationEventDispatcherOwner.current

  public val current: NavigationEventDispatcherOwner?
    @Composable
    get() = if (current_Original == null) {
      // Created by FakeOnBackPressedDispatcherOwnerDump
      val owner = FakeNavigationEventDispatcherOwner()
      InteractivePreviewBackNavigationUpdater.setNavigationEventDispatcherOwner(owner)
      owner
    } else {
      current_Original
    }

    public infix fun provides(...)
}

Once we've modified the LocalNavigationEventDispatcherOwner we can then proceed on creating the dump file:
1) Obtain a .class file from LocalNavigationEventDispatcherOwner with kotlinc
2) Unpack the newly created ".jar" and get LocalNavigationEventDispatcherOwner.class file
3) Use ASMifier to create the dump code. you need to download the needed jar to use ASMifier from these links:
    * Asm: https://repo1.maven.org/maven2/org/ow2/asm/asm/9.7/
    * Asm-utils: https://repo1.maven.org/maven2/org/ow2/asm/asm-util/9.7/
4) Run "echo ./LocalNavigationEventDispatcherOwner.class | xargs -n 1 java -cp "asm-9.7.jar:asm-util-9.7.jar" org.objectweb.asm.util.ASMifier > LocalNavigationEventDispatcherOwnerDump.java
5) Open the newly created "LocalNavigationEventDispatcherOwnerDump.java" file
6) From the dump code detect the code related with the [getCurrent] method.
    Copy the code that visit the [getCurrent] method, in our case is that goes from these portions of code

    ```
    methodVisitor.visitCode()
    val label0 = Label()
    methodVisitor.visitLabel(label0)
    methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
    methodVisitor.visitLdcInsn("C(<get-current>)48@2120L16:LocalNavigationEventDispatcherOwner.kt#wc8b4r")
    ```
    to the visit end method

    ```
    methodVisitor.visitEnd()
    ```
 8) Add the function that calls [InteractivePreviewBackNavigationUpdater.setNavigationEventDispatcherOwner] see also setNavigationEventDispatcherOwner

 Go to go/how-to-ASMify-Navigation3-loaders for more info.
 */

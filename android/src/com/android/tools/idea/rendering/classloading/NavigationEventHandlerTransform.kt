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
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

private const val LOCAL_NAVIGATION_EVENT_HANDLER_CLASS_PATH = "androidx/navigationevent/compose/NavigationEventHandler_androidKt"
private const val IS_INSPECTION_MODE_METHOD_NAME = "isInspectionMode"

/**
 * A [ClassVisitor] that transforms `NavigationEventHandler` to force `isInspectionMode` to return `false`.
 *
 * TODO(b/483631567): Remove this transform (along with LocalNavigationEventTransform) once we implement the permanent solution.
 * TODO(b/501380551): Inspection mode is disabled in androidx navigation3 library version 1.1.0, which breaks Predictive Back in Interactive
 *   Preview. This transform overrides that behavior to ensure Predictive Back works correctly in the preview environment. Remove this
 *   transform when navigation3 version would be updated to version subsequent to 1.1.0
 */
class NavigationEventHandlerTransform(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {
  override val uniqueId: String = NavigationEventHandlerTransform::class.qualifiedName!!

  private var isNavigationEventHandlerClass = false

  override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String?>?) {
    isNavigationEventHandlerClass = name == LOCAL_NAVIGATION_EVENT_HANDLER_CLASS_PATH
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitMethod(
    access: Int,
    name: String?,
    descriptor: String?,
    signature: String?,
    exceptions: Array<out String?>?,
  ): MethodVisitor? {
    val delegate = super.visitMethod(access, name, descriptor, signature, exceptions) ?: return null
    // Ensure we visit and transform the method only if Predictive Back feature is enabled and we have found the
    // [NavigationEventHandler_androidKt] class and the [isInspectionMode] method.
    return if (
      StudioFlags.COMPOSE_INTERACTIVE_PREVIEW_PREDICTIVE_BACK.get() &&
        isNavigationEventHandlerClass &&
        name == IS_INSPECTION_MODE_METHOD_NAME
    ) {
      LocalInspectionModeMethodVisitor(delegate)
    } else {
      delegate
    }
  }

  /**
   * This ASMified code is used to override isInspectionMode, and it returns false
   *
   * Actual code in androidx navigation3 version 1.1.0:
   *     @Composable
   *     internal actual fun isInspectionMode(): Boolean = LocalInspection.current
   *
   * Transformed code:
   *     @Composable
   *     internal actual fun isInspectionMode(): Boolean = false
   *
   * @param delegate the delegate of the method we want to transform, we didn't use the name "delegate" to avoid colliding
   * overrides in ASM 9.6 getDelegate in JVM.
   */
  private class LocalInspectionModeMethodVisitor(delegate: MethodVisitor) : MethodVisitor(Opcodes.ASM9, delegate) {
    override fun visitInsn(opcode: Int) {
      if (opcode == Opcodes.IRETURN) {
        // As we don't want to return the value we have in the stack, meaning the returned value from LocalInspection.current we pop the
        // current value from the Stack and return false (ICONS_0) instead.
        super.visitInsn(Opcodes.POP)
        super.visitInsn(Opcodes.ICONST_0)
      }
      super.visitInsn(opcode)
    }
  }
}

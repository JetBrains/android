/*
 * Copyright (C) 2019 The Android Open Source Project
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
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

private const val COMPOSE_PREVIEW_ANIMATION_MANAGER = "com/android/tools/idea/compose/preview/animation/ComposePreviewAnimationManagerKt"

/**
 * [ClassVisitor] that intercepts calls to PreviewAnimationClock's notifySubscribe and notifyUnsubscribe and redirects them to corresponding
 * methods in ComposePreviewAnimationManager.
 */
class PreviewAnimationClockMethodTransform(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {
  private var isPreviewAnimationClockClass: Boolean = false
  override val uniqueId: String = PreviewAnimationClockMethodTransform::class.qualifiedName!!

  override fun visit(version: Int,
                     access: Int,
                     name: String,
                     signature: String?,
                     superName: String,
                     interfaces: Array<String>) {
    isPreviewAnimationClockClass = name == "androidx/compose/ui/tooling/animation/PreviewAnimationClock"
                                   || name == "androidx/compose/ui/tooling/preview/animation/PreviewAnimationClock"
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitMethod(access: Int,
                           name: String,
                           desc: String,
                           signature: String?,
                           exceptions: Array<String>?): MethodVisitor {
    val mv = super.visitMethod(access, name, desc, signature, exceptions)
    if (!isPreviewAnimationClockClass) return mv
    if (name == "notifySubscribe") {
      mv.visitVarInsn(Opcodes.ALOAD, 0) // PreviewAnimationClock object.
      mv.visitVarInsn(Opcodes.ALOAD, 1) // Animation object
      mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                         COMPOSE_PREVIEW_ANIMATION_MANAGER,
                         "animationSubscribed",
                         "(Ljava/lang/Object;Ljava/lang/Object;)V",
                         false)
    }
    else if (name == "notifyUnsubscribe") {
      mv.visitVarInsn(Opcodes.ALOAD, 1) // Animation object
      mv.visitMethodInsn(Opcodes.INVOKESTATIC, COMPOSE_PREVIEW_ANIMATION_MANAGER, "animationUnsubscribed", "(Ljava/lang/Object;)V", false)
    }
    return mv
  }
}
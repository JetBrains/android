/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.rendering.classloading

import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

/**
 * Transform that detects and records code execution. Currently, its level of granularity is Class,
 * but this might change in the future (we might want to keep tack of line by line execution for
 * coverage). It tracks every call to a method or field and records the [Class] of that method or
 * field. That allows for tracking all the [Class]es that were used in the user code execution. That
 * way we can detect changes to what classes might affect the result of that code execution, in
 * particular rendering. [ref] is used to track down the instance of the transform that tracked the
 * class. This is useful if we have several [ClassLoader]s operating in parallel.
 */
class CodeExecutionTrackerTransform(delegate: ClassVisitor, private val ref: String) :
  ClassVisitor(Opcodes.ASM9, delegate) {
  private var className: String? = null

  override fun visit(
    version: Int,
    access: Int,
    name: String,
    signature: String?,
    superName: String?,
    interfaces: Array<out String>?,
  ) {
    super.visit(version, access, name, signature, superName, interfaces)
    className = name
  }

  override fun visitMethod(
    access: Int,
    name: String?,
    descriptor: String?,
    signature: String?,
    exceptions: Array<out String>?,
  ): MethodVisitor {

    return MethodUsageTrackerVisitor(
      ref,
      className!!,
      super.visitMethod(access, name, descriptor, signature, exceptions),
    )
  }
}

private class MethodUsageTrackerVisitor(
  private val ref: String,
  private val className: String,
  visitor: MethodVisitor,
) : MethodVisitor(Opcodes.ASM9, visitor) {
  /** This is to track the method usage (call). */
  override fun visitCode() {
    reportClass(className)
    super.visitCode()
  }

  /** This is to track the field usage (access). */
  override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
    owner?.let { reportClass(it) }
    super.visitFieldInsn(opcode, owner, name, descriptor)
  }

  private fun reportClass(fqcn: String) {
    visitLdcInsn(ref)
    visitLdcInsn(fqcn)
    visitMethodInsn(
      Opcodes.INVOKESTATIC,
      "com/android/tools/rendering/classloading/ClassesTracker",
      "trackClass",
      "(Ljava/lang/String;Ljava/lang/String;)V",
      false,
    )
  }
}

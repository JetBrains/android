/*
 * Copyright (C) 2021 The Android Open Source Project
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

/**
 * [ClassVisitor] that makes androidx.core.provider.RequestExecutor#execute a no-op method.
 * This method is used by the AndroidX support library to go fetch downloadable fonts in a separate thread.
 * This should simply be ignored when running in Studio, as it doesn't allow the creation of new threads during rendering,
 * and it has its own way of getting downloadable fonts.
 */
class RequestExecutorTransform(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {
  private var isRequestExecutorClass: Boolean = false
  override val uniqueId: String = RequestExecutorTransform::class.qualifiedName!!

  override fun visit(version: Int,
                     access: Int,
                     name: String,
                     signature: String?,
                     superName: String,
                     interfaces: Array<String>) {
    isRequestExecutorClass = name == "androidx/core/provider/RequestExecutor"
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitMethod(access: Int,
                           name: String,
                           desc: String,
                           signature: String?,
                           exceptions: Array<String>?): MethodVisitor? {
    val mv = super.visitMethod(access, name, desc, signature, exceptions) ?: return null
    if (isRequestExecutorClass && name == "execute") {
      return NoOpMethodVisitor(mv)
    }
    return mv
  }

  class NoOpMethodVisitor(val delegate: MethodVisitor) : MethodVisitor(Opcodes.ASM9, null) {
    override fun visitCode() {
      delegate.visitInsn(Opcodes.RETURN)
      delegate.visitEnd()
    }
  }
}
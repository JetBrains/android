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
 * Replaces all the occurrences of constructors and superclasses of [fromFqcn] class with the [toFqcn] class. It is the
 * callers responsibility to make sure that the classes are compatible so that replacement does not break the bytecode.
 */
open class ConstructorAndSuperclassReplacingTransform(
  delegate: ClassVisitor,
  private val fromFqcn: String,
  private val toFqcn: String) : ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {
  override val uniqueId: String = this::class.qualifiedName!!

  override fun visitMethod(access: Int,
                           name: String?,
                           descriptor: String?,
                           signature: String?,
                           exceptions: Array<out String>?): MethodVisitor {
    return ConstructorReplacingMethodVisitor(super.visitMethod(access, name, descriptor, signature, exceptions), fromFqcn, toFqcn)
  }

  override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
    val newSuperName = if (superName == fromFqcn) toFqcn else superName
    super.visit(version, access, name, signature, newSuperName, interfaces)
  }
}

/**
 * Replaces every [replaceeFqcn] class constructor with [replacementFqcn] class constructor.
 */
private class ConstructorReplacingMethodVisitor(
  delegate: MethodVisitor,
  val replaceeFqcn: String,
  val replacementFqcn: String) : MethodVisitor(Opcodes.ASM7, delegate) {

  override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?, isInterface: Boolean) {
    val newOwner = if (owner == replaceeFqcn && name == "<init>") replacementFqcn else owner
    super.visitMethodInsn(opcode, newOwner, name, descriptor, isInterface)
  }

  override fun visitTypeInsn(opcode: Int, type: String?) {
    val newType = if (type == replaceeFqcn && opcode == Opcodes.NEW) replacementFqcn else type
    super.visitTypeInsn(opcode, newType)
  }
}

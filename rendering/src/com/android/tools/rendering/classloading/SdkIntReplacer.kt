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
package com.android.tools.rendering.classloading

import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

class SdkIntReplacer(delegate: ClassVisitor) :
  StaticFieldReplacer(
    delegate,
    "android/os/Build\$VERSION",
    "SDK_INT",
    "com/android/layoutlib/bridge/impl/RenderAction",
    "sSimulatedSdk",
  ),
  ClassVisitorUniqueIdProvider {
  override val uniqueId: String = SdkIntReplacer::class.qualifiedName!!
}

/**
 * Replaces all calls to the static field [originalOwner]#[originalName] with calls to another
 * static field [newOwner]#[newName]. The two fields need to be of the same type.
 */
open class StaticFieldReplacer(
  delegate: ClassVisitor,
  private val originalOwner: String,
  private val originalName: String,
  private val newOwner: String,
  private val newName: String,
) : ClassVisitor(Opcodes.ASM9, delegate) {

  override fun visitMethod(
    access: Int,
    name: String?,
    descriptor: String?,
    signature: String?,
    exceptions: Array<out String>?,
  ): MethodVisitor {
    return StaticFieldMethodVisitor(
      super.visitMethod(access, name, descriptor, signature, exceptions),
      originalOwner,
      originalName,
      newOwner,
      newName,
    )
  }

  private class StaticFieldMethodVisitor(
    delegate: MethodVisitor,
    private val originalOwner: String,
    private val originalName: String,
    private val newOwner: String,
    private val newName: String,
  ) : MethodVisitor(Opcodes.ASM9, delegate) {
    override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
      if (opcode == Opcodes.GETSTATIC && name == originalName && owner == originalOwner) {
        super.visitFieldInsn(Opcodes.GETSTATIC, newOwner, newName, descriptor)
      } else {
        super.visitFieldInsn(opcode, owner, name, descriptor)
      }
    }
  }
}

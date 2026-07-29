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
package com.android.tools.rendering.classloading

import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type

private const val ORIGINAL_SUFFIX = "_Original"

/**
 * [ClassVisitor] that intercepts static calls to `androidx.core.provider.FontsContractCompat` method variants (`fetchFonts`, `requestFont`,
 * `requestFontWithFallbackChain`) during layoutlib classloading and delegates resolution to
 * [com.android.tools.rendering.FontsContractCompatBridge].
 */
class FontsContractCompatTransform(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {

  private var isFontsContractCompatClass = false
  override val uniqueId: String = FontsContractCompatTransform::class.qualifiedName!!

  override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<String>?) {
    isFontsContractCompatClass = name == "androidx/core/provider/FontsContractCompat"
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<String>?): MethodVisitor? {
    val superMv = super.visitMethod(access, name, desc, signature, exceptions)
    val isTargetMethod = name == "fetchFonts" || name == "requestFont" || name == "requestFontWithFallbackChain"
    if (!isFontsContractCompatClass || !isTargetMethod) {
      return superMv
    }
    if (superMv != null) {
      if (name == "fetchFonts") {
        emitFetchFontsWrapper(superMv, desc)
      } else {
        emitRequestFontWrapper(superMv, desc)
      }
    }

    val originalAccess = (access and Opcodes.ACC_PUBLIC.inv() and Opcodes.ACC_PROTECTED.inv()) or Opcodes.ACC_PRIVATE
    return super.visitMethod(originalAccess, name + ORIGINAL_SUFFIX, desc, signature, exceptions)
  }

  /**
   * Generates bytecode for the `fetchFonts` wrapper method. Invokes [com.android.tools.rendering.FontsContractCompatBridge.fetchFonts] and
   * returns the result directly.
   */
  private fun emitFetchFontsWrapper(mv: MethodVisitor, desc: String) {
    mv.visitCode()
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitVarInsn(Opcodes.ALOAD, 1)
    mv.visitVarInsn(Opcodes.ALOAD, 2)
    mv.visitMethodInsn(
      Opcodes.INVOKESTATIC,
      "com/android/tools/rendering/FontsContractCompatBridge",
      "fetchFonts",
      "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
      false,
    )
    mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getReturnType(desc).internalName)
    mv.visitInsn(Opcodes.ARETURN)
    mv.visitMaxs(3, 3)
    mv.visitEnd()
  }

  /**
   * Generates bytecode for `requestFont` / `requestFontWithFallbackChain` wrapper methods. Invokes
   * [com.android.tools.rendering.FontsContractCompatBridge.requestFont] and returns the result directly.
   */
  private fun emitRequestFontWrapper(mv: MethodVisitor, desc: String) {
    val returnType = Type.getReturnType(desc)
    val argTypes = Type.getArgumentTypes(desc)
    val numArgsSlots = argTypes.sumOf { it.size }

    mv.visitCode()

    val callbackIdx = argTypes.indexOfFirst { it.className.endsWith("FontRequestCallback") }
    val styleIdx = argTypes.indexOfFirst { it == Type.INT_TYPE }

    val callbackSlot = if (callbackIdx >= 0) argTypes.asSequence().take(callbackIdx).sumOf { it.size } else -1
    val styleSlot = if (styleIdx >= 0) argTypes.asSequence().take(styleIdx).sumOf { it.size } else -1

    // 1. Context (arg 0)
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    // 2. FontRequest or List<FontRequest> (arg 1)
    mv.visitVarInsn(Opcodes.ALOAD, 1)
    // 3. Callback (or null if absent)
    if (callbackSlot >= 0) {
      mv.visitVarInsn(Opcodes.ALOAD, callbackSlot)
    } else {
      mv.visitInsn(Opcodes.ACONST_NULL)
    }
    // 4. Style (or 0 if absent)
    if (styleSlot >= 0) {
      mv.visitVarInsn(Opcodes.ILOAD, styleSlot)
    } else {
      mv.visitInsn(Opcodes.ICONST_0)
    }

    mv.visitMethodInsn(
      Opcodes.INVOKESTATIC,
      "com/android/tools/rendering/FontsContractCompatBridge",
      "requestFont",
      "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;I)Ljava/lang/Object;",
      false,
    )

    if (returnType == Type.VOID_TYPE) {
      mv.visitInsn(Opcodes.POP)
      mv.visitInsn(Opcodes.RETURN)
    } else {
      mv.visitTypeInsn(Opcodes.CHECKCAST, returnType.internalName)
      mv.visitInsn(Opcodes.ARETURN)
    }

    mv.visitMaxs(4, numArgsSlots)
    mv.visitEnd()
  }
}

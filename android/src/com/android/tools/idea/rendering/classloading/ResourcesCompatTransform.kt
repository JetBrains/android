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
 * [ClassVisitor] that fixes a hardcoded path in ResourcesCompat.loadFont.
 * In this method, there is a check that font files have a path that starts with res/.
 * This is not the case in Studio. This replaces the check with one that verifies that the path contains res/.
 */
class ResourcesCompatTransform(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {
  private var isResourcesCompatClass: Boolean = false
  override val uniqueId: String = ResourcesCompatTransform::class.qualifiedName!!

  override fun visit(version: Int,
                     access: Int,
                     name: String,
                     signature: String?,
                     superName: String,
                     interfaces: Array<String>) {
    isResourcesCompatClass = name == "androidx/core/content/res/ResourcesCompat"
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitMethod(access: Int,
                           name: String,
                           desc: String,
                           signature: String?,
                           exceptions: Array<String>?): MethodVisitor {
    val mv = super.visitMethod(access, name, desc, signature, exceptions)
    if (isResourcesCompatClass && name == "loadFont"
        && desc == "(Landroid/content/Context;Landroid/content/res/Resources;Landroid/util/TypedValue;II" +
        "Landroidx/core/content/res/ResourcesCompat\$FontCallback;Landroid/os/Handler;ZZ)Landroid/graphics/Typeface;") {
      return LoadFontVisitor(mv)
    }
    return mv;
  }

  private class LoadFontVisitor(delegate: MethodVisitor) : MethodVisitor(Opcodes.ASM7, delegate) {
    override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?, isInterface: Boolean) {
      if ("startsWith" == name) {
        super.visitMethodInsn(opcode, owner, "contains", "(Ljava/lang/CharSequence;)Z", isInterface)
      }
      else {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
      }
    }
  }
}
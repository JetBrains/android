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
package com.android.tools.idea.rendering.classloading

import com.android.tools.rendering.classloading.ClassVisitorUniqueIdProvider
import com.android.tools.rendering.classloading.fromPackageNameToBinaryName
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.FieldVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

private class StringReplaceMethodTransform(
  delegate: MethodVisitor,
  private val constantMap: Map<String, String>,
) : MethodVisitor(Opcodes.ASM9, delegate) {
  override fun visitLdcInsn(value: Any?) =
    if (value is String) super.visitLdcInsn(constantMap.getOrDefault(value, value))
    else super.visitLdcInsn(value)
}

/**
 * A [ClassVisitor] that replaces `LDC` calls of strings with other strings passed as part of the
 * `inputReplaceMap`. The `inputReplaceMap` has a key the class name that this class will inspect.
 * Other classes will be ignored by this transform.
 *
 * The key of `inputReplaceMap` contains a map "Old String" -> "New String".
 */
class StringReplaceTransform(
  delegate: ClassVisitor,
  inputReplaceMap: Map<String, Map<String, String>>,
) : ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {
  @Suppress("UnstableApiUsage")
  override val uniqueId: String =
    StringReplaceTransform::class.qualifiedName +
      "," +
      com.google.common.hash.Hashing.goodFastHash(64)
        .newHasher()
        .putString(
          inputReplaceMap.map { "${it.key}=${it.value}" }.joinToString("\n"),
          Charsets.UTF_8,
        )
        .hash()
        .toString()

  private val replaceMap =
    inputReplaceMap.map { it.key.fromPackageNameToBinaryName() to it.value }.toMap()
  private var constantMap: Map<String, String> = emptyMap()

  override fun visit(
    version: Int,
    access: Int,
    name: String?,
    signature: String?,
    superName: String?,
    interfaces: Array<out String>?,
  ) {
    constantMap = replaceMap.getOrDefault(name, emptyMap())
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitField(
    access: Int,
    name: String?,
    descriptor: String?,
    signature: String?,
    value: Any?,
  ): FieldVisitor =
    if (value is String && constantMap.isNotEmpty())
      super.visitField(access, name, descriptor, signature, constantMap.getOrDefault(value, value))
    else super.visitField(access, name, descriptor, signature, value)

  override fun visitMethod(
    access: Int,
    name: String?,
    descriptor: String?,
    signature: String?,
    exceptions: Array<out String>?,
  ): MethodVisitor =
    if (constantMap.isEmpty()) super.visitMethod(access, name, descriptor, signature, exceptions)
    else
      StringReplaceMethodTransform(
        super.visitMethod(access, name, descriptor, signature, exceptions),
        constantMap,
      )
}

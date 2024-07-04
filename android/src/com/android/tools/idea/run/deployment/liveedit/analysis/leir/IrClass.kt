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
package com.android.tools.idea.run.deployment.liveedit.analysis.leir

import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.tree.ClassNode

class IrClass(node: ClassNode) {
  constructor(classBytes: ByteArray) : this(classBytes.toClassNode())

  val version = node.version
  val name: String = node.name
  val access = parseAccess(node.access)
  val signature: String? = node.signature
  val superName: String? = node.superName
  val interfaces: Set<String> = node.interfaces.toSet()
  val enclosingMethod = getEnclosingMethod(node.outerClass, node.outerMethod, node.outerMethodDesc)
  val sourceFile: String? = node.sourceFile

  val methods = node.methods.map { IrMethod(this, it) }
  val fields = node.fields.map(::IrField)
  val annotations = toAnnotationList(node.visibleAnnotations, node.invisibleAnnotations)

  // We currently ignore the following ClassNode members when parsing, because they're either irrelevant to LiveEdit, don't appear in the
  // classfile, or are metadata about other information that we're already parsing.
  //  - sourceDebug
  //  - module
  //  - attrs
  //  - recordComponents
  //  - permittedSubclasses
  //  - innerClasses
  //  - nestHostClass
  //  - nestMembers
  //  - invisibleTypeAnnotations
  //  - visibleTypeAnnotations
}

/**
 * A representation of the classfile [EnclosingMethod](https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.7) attribute.
 */
data class EnclosingMethod(val outerClass: String?, val outerMethod: String?, val outerMethodDesc: String?)

private fun ByteArray.toClassNode(): ClassNode {
  val node = ClassNode()
  val reader = ClassReader(this)
  reader.accept(node, 0)
  return node
}

private fun getEnclosingMethod(outerClass: String?, outerMethod: String?, outerMethodDesc: String?): EnclosingMethod? {
  return if (outerClass == null && outerMethod == null && outerMethodDesc == null) null
  else EnclosingMethod(outerClass, outerMethod, outerMethodDesc)
}
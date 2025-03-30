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

import org.jetbrains.org.objectweb.asm.tree.FieldNode

class IrField(node: FieldNode) {
  val name: String = node.name ?: throw IllegalArgumentException("Field with null name")
  val desc: String? = node.desc
  val access = parseAccess(node.access)
  val signature: String? = node.signature
  val value: Any? = node.value

  val annotations = toAnnotationList(node.visibleAnnotations, node.invisibleAnnotations)

  // We currently ignore the following FieldNode members when parsing, because they're either irrelevant to LiveEdit, don't appear in the
  // classfile, or are metadata about other information that we're already parsing.
  // - attrs
  // - invisibleTypeAnnotations
  // - visibleTypeAnnotations
}
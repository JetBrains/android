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
package com.android.tools.idea.run.deployment.liveedit.analysis

import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAnnotation
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.intellij.openapi.util.TextRange

// Annotation type for KeyMeta annotations
private const val KEY_META = "Landroidx/compose/runtime/internal/FunctionKeyMeta;"

// Annotation type that contains multiple KeyMeta annotations
private const val KEY_META_CONTAINER = "Landroidx/compose/runtime/internal/FunctionKeyMeta\$Container;"

// Annotation parameter name for the array of KeyMeta annotations
private const val KEY_META_ARRAY = "value"

data class ComposeGroup(val key: Int, val range: TextRange)

fun parseComposeGroups(keyMetaClass: IrClass): List<ComposeGroup> {
  val annotations = keyMetaClass.annotations.associateBy { it.desc }

  // Handle case of single @FunctionKeyMeta annotation
  if (annotations.containsKey(KEY_META)) {
    return listOf(toComposeGroup(annotations[KEY_META]!!))
  }

  val functionKeyMetaContainer = annotations[KEY_META_CONTAINER] ?: throw IllegalStateException()
  val keyMetaList = mutableListOf<ComposeGroup>()

  (functionKeyMetaContainer.values[KEY_META_ARRAY] as List<*>).forEach {
    val annotation = it as IrAnnotation
    keyMetaList.add(toComposeGroup(annotation))
  }

  return keyMetaList
}

private fun toComposeGroup(annotation: IrAnnotation): ComposeGroup {
  val range = TextRange.create(annotation.values["startOffset"] as Int, annotation.values["endOffset"] as Int)
  return ComposeGroup(annotation.values["key"] as Int, range)
}
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

import org.jetbrains.org.objectweb.asm.tree.AnnotationNode

class IrAnnotation(annotation: AnnotationNode) {
  val desc: String = annotation.desc ?: throw IllegalArgumentException("Annotation with null type descriptor")
  val values: Map<String, Any?>

  init {
    values = mutableMapOf()
    val size = annotation.values?.size ?: 0
    for (i in 0 until size step 2) {
      val name = annotation.values[i] as String

      // The value may be:
      // - a primitive (Byte, Boolean, Character, Short, Integer, Long, Float, Double)
      // - a String
      // - an ASM Type object
      // - a two-element string array (representing an enum)
      // - an AnnotationNode
      // - a List of values of one of the preceding types.

      // All of these types except AnnotationNode can be compared with equals(), so we transform
      // AnnotationNode -> ParsedAnnotation in order to directly compare annotations.
      when (val value = annotation.values.getOrNull(i + 1)) {
        is List<*> -> {
          when (value.firstOrNull()) {
            is AnnotationNode -> values[name] = value.map { IrAnnotation(it as AnnotationNode) }
            else -> values[name] = value
          }
        }

        is Array<*> -> {
          values[name] = value.toList()
        }

        is AnnotationNode -> {
          values[name] = IrAnnotation(value)
        }

        else -> {
          values[name] = value
        }
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    if (javaClass != other?.javaClass) {
      return false
    }

    other as IrAnnotation
    return desc == other.desc && values == other.values
  }

  override fun hashCode(): Int {
    var result = desc.hashCode()
    result = 31 * result + values.hashCode()
    return result
  }
}

fun toAnnotationList(visible: List<AnnotationNode>?, invisible: List<AnnotationNode>?): List<IrAnnotation> {
  return (visible.orEmpty() + invisible.orEmpty()).map(::IrAnnotation)
}
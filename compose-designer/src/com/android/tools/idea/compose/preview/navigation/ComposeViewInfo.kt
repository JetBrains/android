/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.navigation

import kotlin.math.abs

/**
 * Information needed for creating custom scene components later.
 */
data class ComposeViewInfo(val sourceLocation: SourceLocation,
                           val bounds: PxBounds,
                           val children: List<ComposeViewInfo>) {
  override fun toString(): String =
    """${sourceLocation}
      |bounds=(top=${bounds.top.value}, left=${bounds.left.value}, bottom=${bounds.bottom.value}, right=${bounds.right.value})
      |childCount=${children.size}""".trimMargin()

  fun allChildren(): List<ComposeViewInfo> = listOf(this) + children.flatMap { it.allChildren() }
}

/**
 * Pixel bounds. The model closely resembles how Compose Stack is returned.
 */
data class PxBounds(
  val left: Px,
  val top: Px,
  val right: Px,
  val bottom: Px) {
  val width = right - left
  val height = bottom - top
}

/**
 * Pixel float. The model closely resembles how Compose Stack is returned.
 */
data class Px(val value: Float) {
  companion object {
    val Zero: Px = Px(0f)
  }

  override fun equals(other: Any?): Boolean {
    return other is Px && (abs(other.value - value) < 0.01f)
  }

  operator fun minus(other: Px): Px {
    return Px(this.value - other.value)
  }

  fun toInt(): Int {
    return value.toInt()
  }

  override fun hashCode(): Int {
    return value.hashCode()
  }
}

/**
 * Returns true if bounds exist in the list
 */
fun boundsExist(bound: PxBounds, list: List<ComposeViewInfo>): Boolean {
  return list.any { it.bounds == bound }
}

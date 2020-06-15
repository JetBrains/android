/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview


/**
 * Information needed for creating custom scene components later.
 */
data class ComposeBoundInfo(val fileName: String,
                            var lineNumber: Int,
                            val bounds: PxBounds) {
  override fun toString(): String =
    """($fileName:$lineNumber, bounds=(top=${bounds.top.value}, left=${bounds.left.value}, bottom=${bounds.bottom.value}, right=${bounds.right.value})"""
}

/**
 * Tree representation of ViewInfo in case this becomes useful in the future. (Specially around overlapping regions.
 * It closely resembles how Compose stack trace is currently returned.
 */
data class ComposeBoundInfoTree(
  val info: ComposeBoundInfo,
  val children: List<ComposeBoundInfoTree>
) {

  fun stream(): Sequence<ComposeBoundInfo> {
    return sequenceOf(this.info) +  children.flatMap { it.stream().asIterable() }
  }

  override fun toString(): String =
    """$info, childrenCount=${children.size})"""
}

/**
 * Pixel bounds. The model closely resembles how Compose Stack is returned.
 */
class PxBounds(
  val left: Px,
  val top: Px,
  val right: Px,
  val bottom: Px
) {

  val width = right - left
  val height = bottom - top

  override fun hashCode(): Int {
    return toString().hashCode()
  }

  override fun toString(): String {
    return """bounds=(top=${top.value}, left=${left.value}, bottom=${bottom.value}, right=${right.value})"""
  }

  override fun equals(other: Any?): Boolean {
    return other is PxBounds && other.left == left && other.right == right && other.top == top && other.bottom == bottom
  }
}

/**
 * Pixel float. The model closely resembles how Compose Stack is returned.
 */
class Px(val value: Float) {
  companion object {
    val Zero: Px = Px(0f)
  }

  override fun equals(other: Any?): Boolean {
    return other is Px && (Math.abs(other.value - value) < 0.01f)
  }

  operator fun minus(other: Px): Px {
    return Px(this.value - other.value)
  }

  fun toInt(): Int {
    return value.toInt()
  }

  override fun toString(): String {
    return value.toString()
  }
}

/**
 * Returns true if bounds exist in the list
 */
fun boundsExist(bound: PxBounds, list: List<ComposeBoundInfo>): Boolean {
  return list.any { it.bounds == bound }
}

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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.common.model.AndroidCoordinate
import com.google.common.annotations.VisibleForTesting

/** Information needed for creating custom scene components later. */
data class ComposeViewInfo(
  val sourceLocation: SourceLocation,
  val bounds: PxBounds,
  val children: List<ComposeViewInfo>
) {
  override fun toString(): String =
    """${sourceLocation}
      |   bounds=(top=${bounds.top}, left=${bounds.left}, bottom=${bounds.bottom}, right=${bounds.right})
      |   childCount=${children.size}""".trimMargin()

  fun allChildren(): List<ComposeViewInfo> = listOf(this) + children.flatMap { it.allChildren() }
}

@VisibleForTesting
fun ComposeViewInfo.findHitWithDepth(
  x: Int,
  y: Int,
  depth: Int = 0
): Collection<Pair<Int, ComposeViewInfo>> =
  if (bounds.isNotEmpty() && bounds.containsPoint(x, y)) {
    listOf(Pair(depth, this)) + children.flatMap { it.findHitWithDepth(x, y, depth + 1) }.toList()
  } else {
    listOf()
  }

fun List<ComposeViewInfo>.findHitWithDepth(
  x: Int,
  y: Int,
  depth: Int = 0
): Collection<Pair<Int, ComposeViewInfo>> = flatMap { it.findHitWithDepth(x, y, depth) }

fun ComposeViewInfo.findDeepestHits(
  @AndroidCoordinate x: Int,
  @AndroidCoordinate y: Int
): Collection<ComposeViewInfo> =
  findHitWithDepth(x, y)
    .groupBy { it.first }
    .maxByOrNull { it.key }
    ?.value
    ?.map { it.second }
    ?.toList()
    ?: emptyList()

/** Pixel bounds. The model closely resembles how Compose Stack is returned. */
data class PxBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
  val width = right - left
  val height = bottom - top
}

@VisibleForTesting
fun PxBounds.containsPoint(@AndroidCoordinate x: Int, @AndroidCoordinate y: Int): Boolean =
  x in left..right && y in top..bottom

@VisibleForTesting fun PxBounds.area(): Int = (right - left) * (bottom - top)

@VisibleForTesting fun PxBounds.isEmpty(): Boolean = area() == 0

@VisibleForTesting fun PxBounds.isNotEmpty(): Boolean = !isEmpty()

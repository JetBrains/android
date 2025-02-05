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

import com.android.sdklib.AndroidCoordinate
import com.google.common.annotations.VisibleForTesting
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push

/** Information needed for creating custom scene components later. */
data class ComposeViewInfo(
  val sourceLocation: SourceLocation,
  val bounds: PxBounds,
  val children: List<ComposeViewInfo>,
  val name: String,
) {
  override fun toString(): String =
    """$sourceLocation
      |   name=${name}
      |   bounds=(top=${bounds.top}, left=${bounds.left}, bottom=${bounds.bottom}, right=${bounds.right})
      |   childCount=${children.size}"""
      .trimMargin()

  fun allChildren(): List<ComposeViewInfo> = listOf(this) + children.flatMap { it.allChildren() }
}

@VisibleForTesting
fun ComposeViewInfo.findHitWithDepth(
  x: Int,
  y: Int,
  depth: Int = 0,
): Collection<Pair<Int, ComposeViewInfo>> =
  if (containsPoint(x, y)) {
    listOf(Pair(depth, this)) + children.flatMap { it.findHitWithDepth(x, y, depth + 1) }.toList()
  } else {
    listOf()
  }

fun List<ComposeViewInfo>.findHitWithDepth(
  x: Int,
  y: Int,
  depth: Int = 0,
): Collection<Pair<Int, ComposeViewInfo>> = flatMap { it.findHitWithDepth(x, y, depth) }

fun List<ComposeViewInfo>.findSmallestHit(
  @AndroidCoordinate x: Int,
  @AndroidCoordinate y: Int,
): Collection<ComposeViewInfo> {
  val viewInfos = first().findAllLeafHits(x, y)
  if (viewInfos.isEmpty()) return listOf()
  return listOf(
    viewInfos.minByOrNull {
      (it.bounds.bottom - it.bounds.top) * (it.bounds.right - it.bounds.left)
    }!!
  )
}

/**
 * To be able to find every possible hit we need to find each leaf node of the tree within the file
 * we are looking for. A leaf node being defined as living in this file and having no children that
 * live in the file.
 */
fun ComposeViewInfo.findLeafHitsInFile(x: Int, y: Int, fileName: String): List<ComposeViewInfo> {
  return this.findAllLeafHits(x, y).filter { it.sourceLocation.fileName == fileName }
}

/** This function will return all hits that have no children. */
fun ComposeViewInfo.findAllLeafHits(x: Int, y: Int): List<ComposeViewInfo> {
  if (!this.containsPoint(x, y)) return emptyList()
  val leafHits = mutableListOf<ComposeViewInfo>()
  val stack = mutableListOf(this)

  while (stack.isNotEmpty()) {
    var currentViewInfo: ComposeViewInfo = stack.pop()
    var childrenContainingPoint = currentViewInfo.children.filter { it.containsPoint(x, y) }

    // If no children contain point then it must be a leaf
    if (childrenContainingPoint.isEmpty()) {
      leafHits.push(currentViewInfo)
    } else {
      stack.addAll(childrenContainingPoint)
    }
  }
  return leafHits.toList()
}

/** This function will return all ComposeViewInfo objects recursively that are in the given file. */
fun ComposeViewInfo.findAllHitsInFile(fileName: String): List<ComposeViewInfo> {
  if (!this.doesFileExistInTree(fileName)) return emptyList()
  val stack = mutableListOf(this)
  val hits = mutableListOf<ComposeViewInfo>()

  while (stack.isNotEmpty()) {
    val currentViewInfo: ComposeViewInfo = stack.pop()
    val childrenContainingPoint =
      currentViewInfo.children.filter { it.doesFileExistInTree(fileName) }

    if (currentViewInfo.sourceLocation.fileName == fileName) hits.push(currentViewInfo)
    stack.addAll(childrenContainingPoint)
  }
  return hits.toList()
}

fun ComposeViewInfo.containsPoint(x: Int, y: Int): Boolean {
  return bounds.isNotEmpty() && bounds.containsPoint(x, y)
}

fun ComposeViewInfo.doesFileExistInTree(fileName: String): Boolean {
  return sourceLocation.fileName == fileName || children.any { it.doesFileExistInTree(fileName) }
}

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

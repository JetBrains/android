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
fun ComposeViewInfo.findHitWithDepth(x: Int, y: Int): Collection<Pair<Int, ComposeViewInfo>> {
  val hitsWithDepth = mutableListOf<Pair<Int, ComposeViewInfo>>()
  val stack = mutableListOf<Pair<Int, ComposeViewInfo>>()

  // Add top level
  stack.push(Pair(0, this))

  while (stack.isNotEmpty()) {
    val current = stack.pop()
    val currentViewInfo = current.second

    // Add to results if it contains point
    if (currentViewInfo.containsPoint(x, y)) {
      hitsWithDepth.push(current)
    }

    // Add all children to stack
    currentViewInfo.children.forEach { child -> stack.push(Pair(current.first + 1, child)) }
  }
  return hitsWithDepth
}

/**
 * Traverses the compose view tree to compile a list of view information that contain the specified
 * x, y coordinates, along with their corresponding depth in the tree hierarchy.
 */
fun List<ComposeViewInfo>.findHitWithDepth(x: Int, y: Int): Collection<Pair<Int, ComposeViewInfo>> {
  return flatMap { it.findHitWithDepth(x, y) }
}

/**
 * Traverses the compose view tree and finds all leaf hits that have the coordinates [x] and [y].
 * Then goes through each hit and finds the smallest [ComposeViewInfo] by area and returns it in a
 * [Collection].
 */
fun ComposeViewInfo.findSmallestHit(
  @AndroidCoordinate x: Int,
  @AndroidCoordinate y: Int,
): Collection<ComposeViewInfo> {
  val viewInfos = findAllHitsWithPoint(x, y)
  if (viewInfos.isEmpty()) return listOf()
  return listOf(
    viewInfos.minByOrNull {
      (it.bounds.bottom - it.bounds.top) * (it.bounds.right - it.bounds.left)
    }!!
  )
}

/**
 * Traverses the compose view tree and finds the smallest [ComposeViewInfo] by area and returns it
 * in a [Collection].
 */
fun List<ComposeViewInfo>.findSmallestHit(x: Int, y: Int): Collection<ComposeViewInfo> {
  return flatMap { it.findSmallestHit(x, y) }
}

/**
 * Traverses the compose view tree to compile a list of view information that contain the specified
 * x, y coordinates.
 */
fun ComposeViewInfo.findAllHitsWithPoint(x: Int, y: Int): List<ComposeViewInfo> {
  val hits = mutableListOf<ComposeViewInfo>()
  val stack = mutableListOf(this)

  while (stack.isNotEmpty()) {
    val currentViewInfo: ComposeViewInfo = stack.pop()
    if (currentViewInfo.containsPoint(x, y)) {
      hits.push(currentViewInfo)
    }
    stack.addAll(currentViewInfo.children)
  }
  return hits
}

/**
 * Traverses the compose view tree to compile a [Collection] of [ComposeViewInfo] that contain the
 * specified x, y coordinates.
 */
fun List<ComposeViewInfo>.findAllHitsWithPoint(x: Int, y: Int): Collection<ComposeViewInfo> {
  return flatMap { it.findAllHitsWithPoint(x, y) }
}

/** This function will return all ComposeViewInfo objects recursively that are in the given file. */
fun ComposeViewInfo.findAllHitsInFile(fileName: String): List<ComposeViewInfo> {
  val stack = mutableListOf(this)
  val hits = mutableListOf<ComposeViewInfo>()

  while (stack.isNotEmpty()) {
    val currentViewInfo: ComposeViewInfo = stack.pop()
    if (currentViewInfo.isInFile(fileName)) hits.push(currentViewInfo)
    stack.addAll(currentViewInfo.children)
  }
  return hits.toList()
}

/**
 * Traverses the compose view tree to compile a [Collection] of [ComposeViewInfo] that are in the
 * [fileName] passed in.
 */
fun List<ComposeViewInfo>.findAllHitsInFile(fileName: String): Collection<ComposeViewInfo> {
  return flatMap { it.findAllHitsInFile(fileName) }
}

fun ComposeViewInfo.containsPoint(x: Int, y: Int): Boolean {
  return bounds.isNotEmpty() && bounds.containsPoint(x, y)
}

fun ComposeViewInfo.isInFile(fileName: String): Boolean {
  return sourceLocation.fileName == fileName
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

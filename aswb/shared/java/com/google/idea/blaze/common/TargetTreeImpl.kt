/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.common

import java.nio.file.Path
import kotlin.math.min

/**
 * Encapsulates a set of targets, represented as Labels.
 *
 *
 * This class uses a tree to store the set of targets so that finding all the child targets of a
 * given directory is fast.
 */
class TargetTreeImpl private constructor(private val sortedLabels: List<Label>) : TargetTree {
  override fun getTargets(): Sequence<Label> = getSubpackages(EMPTY_PATH)

  override fun getDirectTargets(pkg: Path): Sequence<Label> =
    sortedLabels
      .subList(getPackageStartIndex(workspace = "", pkg), sortedLabels.size).asSequence()
      .takeWhile { it.workspace == "" && it.`package` == pkg }

  override fun getSubpackages(pkg: Path): Sequence<Label> =
    sortedLabels
      .subList(getPackageStartIndex(workspace = "", pkg), sortedLabels.size).asSequence()
      .takeWhile { it.workspace == "" && it.`package`.startsWithRespectingEmpty(pkg) }

  override val targetCountForStatsOnly: Int
    get() = sortedLabels.size

  private fun getPackageStartIndex(workspace: String, pkg: Path): Int {
    return sortedLabels
      .binarySearch {
        val workspaceResult = it.workspace compareTo workspace
        if (workspaceResult != 0) return@binarySearch workspaceResult
        val packageResult = comparePathsByNames(it.`package`, pkg)
        if (packageResult != 0) return@binarySearch packageResult
        it.name() compareTo ""
      }
      .let { if (it >= 0) it else -it - 1 }
  }

  companion object {
    @JvmField
    val EMPTY: TargetTreeImpl = TargetTreeImpl(sortedLabels = emptyList())

    fun create(targets: Collection<Label>): TargetTree {
      return TargetTreeImpl(
        sortedLabels = targets.sortedWith { a, b ->
          val workspaceResult = a.workspace compareTo b.workspace
          if (workspaceResult != 0) return@sortedWith workspaceResult
          val pkgResult = comparePathsByNames(a.`package`, b.`package`)
          if (pkgResult != 0) return@sortedWith pkgResult
          a.name compareTo b.name
        }
      )
    }
  }
}

private val EMPTY_PATH = Path.of("")
private fun Path.startsWithRespectingEmpty(parent: Path): Boolean {
  return parent == EMPTY_PATH || this.startsWith(parent)
}


private fun comparePathsByNames(p1: Path, p2: Path): Int {
  val nc = min(p1.nameCount, p2.nameCount)
  for (i in 0..<nc) {
    val c = p1.getName(i)compareTo p2.getName(i)
    if (c != 0) {
      return c
    }
  }
  return p1.nameCount compareTo p2.nameCount
}


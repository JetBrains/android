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
package com.google.idea.blaze.qsync.project

import com.google.idea.blaze.common.Label
import java.nio.file.Path

/** The result of resolving a source file or directory to build targets.  */
sealed class TargetsToBuild() {
  abstract val displayLabel: String
  abstract val targets: Set<Label>
  abstract fun isAmbiguous(): Boolean

  /**
   * Represents a group of targets that should be built as per a user's request.
   */
  data class TargetGroup(override val targets: Set<Label>): TargetsToBuild() {
    /** A label for the group of targets to display in user prompts. */
    override val displayLabel: String get() = targets.joinToString(", ", limit = 3)

    /**
     * Indicates whether the set of [targets] is ambiguous and requires refinement.
     */
    override fun isAmbiguous(): Boolean = false
  }

  /**
   * Represents a target or a group of alternative targets that need to be built to build dependencies for the [sourceFile].
   */
  data class SourceFile(override val targets: Set<Label>, private val sourceFile: Path): TargetsToBuild() {
    override val displayLabel: String get() = sourceFile.toString()
    override fun isAmbiguous(): Boolean = targets.size > 1
  }

  object None: TargetsToBuild() {
    override val displayLabel: String get() = "(none)"
    override val targets: Set<Label> = setOf()
    override fun isAmbiguous(): Boolean = false
  }

  fun isEmpty() = targets.isEmpty()
  fun getUnambiguousTargets() = if (isAmbiguous()) setOf() else targets

  /** Returns true if `labels` overlaps with any of the targets to build  */
  fun overlapsWith(targets: Set<Label>): Boolean = (targets intersect this.targets).isNotEmpty()

  companion object {
    @JvmStatic
    fun targetGroup(targets: Collection<Label>) = TargetGroup(targets.toSet())

    @JvmStatic
    fun forSourceFile(targets: Collection<Label>, sourceFile: Path) = SourceFile(targets.toSet(), sourceFile)
  }
}

fun Collection<TargetsToBuild>.getAllUnambiguous(): Set<Label> {
  return asSequence().filter { it.isAmbiguous() }.flatMap { it.targets }.toSet()
}

fun Collection<TargetsToBuild>.getAllAmbiguous(): Set<TargetsToBuild> {
  return asSequence().filter { it.isAmbiguous() }.toSet()
}

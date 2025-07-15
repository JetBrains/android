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
  /** A label for the group of targets to display in user prompts. */
  abstract val displayLabel: String
  abstract val targets: Set<Label>
  /**
   * Indicates whether the set of [targets] is ambiguous and requires refinement.
   */
  abstract fun isAmbiguous(): Boolean

  /**
   * Indicates whether the query phase of sync needs to run in order to fetch target details.
   */
  abstract fun requiresQueryDataRefresh(): Boolean
  open fun autoDisambiguate(anchors: Set<Label>): TargetsToBuild? = null

  /**
   * Represents a group of targets that should be built as per a user's request.
   */
  data class TargetGroup(override val targets: Set<Label>): TargetsToBuild() {
    override val displayLabel: String get() = targets.joinToString(", ", limit = 3)
    override fun requiresQueryDataRefresh(): Boolean = false
    override fun isAmbiguous(): Boolean = false
  }

  /**
   * Represents a target or a group of alternative targets that need to be built to build dependencies for the [sourceFile].
   */
  data class SourceFile(override val targets: Set<Label>, private val sourceFile: Path): TargetsToBuild() {
    override val displayLabel: String get() = sourceFile.toString()
    override fun isAmbiguous(): Boolean = targets.size > 1
    override fun requiresQueryDataRefresh(): Boolean = false
    override fun autoDisambiguate(anchors: Set<Label>): TargetsToBuild? {
      if (!isAmbiguous()) return null
      return targets.find { anchors.contains(it)}?.let { copy(targets = setOf(it)) }
    }
  }

  /**
   * Represents an unknown set of targets that need to be built to build dependencies for the [sourceFile], which is not present in the
   * build graph.
   */
  data class UnknownSourceFile(private val sourceFile: Path): TargetsToBuild() {
    override val displayLabel: String get() = sourceFile.toString()
    override fun isAmbiguous(): Boolean = false
    override fun requiresQueryDataRefresh(): Boolean = true
    override val targets: Set<Label>get() = emptySet()
  }

  object None: TargetsToBuild() {
    override val displayLabel: String get() = "(none)"
    override val targets: Set<Label> = setOf()
    override fun requiresQueryDataRefresh(): Boolean = false
    override fun isAmbiguous(): Boolean = false
  }

  fun isEmpty() = targets.isEmpty()
  fun getUnambiguousTargets() = if (isAmbiguous()) setOf() else targets

  companion object {
    @JvmStatic
    fun targetGroup(targets: Collection<Label>): TargetGroup = TargetGroup(targets.toSet())

    @JvmStatic
    fun forSourceFile(targets: Collection<Label>, sourceFile: Path): SourceFile = SourceFile(targets.toSet(), sourceFile)

    @JvmStatic
    fun forUnknownSourceFile(sourceFile: Path): UnknownSourceFile = UnknownSourceFile(sourceFile)
  }
}


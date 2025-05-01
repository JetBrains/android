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
package com.google.idea.blaze.base.qsync.action

import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.project.TargetsToBuild

/**
 * Additional targets to consider when disambiguating targets to build for a file.
 */
sealed interface TargetDisambiguationAnchors {
  val anchorTargets: Set<Label>

  /**
   * A set of specific targets to consider when disambiguating targets to build for a file.
   */
  data class Targets(override val anchorTargets: Set<Label>) : TargetDisambiguationAnchors

  /**
   * An anchor requesting that the working set be considered when disambiguating targets to build for a file.
   */
  class WorkingSet(private val helper: BuildDependenciesHelper) : TargetDisambiguationAnchors {
    override val anchorTargets: Set<Label> get() = helper.workingSetTargetsIfEnabled
  }

  companion object {
    @JvmField val NONE: TargetDisambiguationAnchors = Targets(emptySet())
  }
}

/** Utility for identifying ambiguities in targets to build for files  */
data class TargetDisambiguator(
  val unambiguousTargets: Set<Label>,
  val ambiguousTargetSets: Set<TargetsToBuild>,
  val undefinedTargetSets: Set<TargetsToBuild>,
) {

  companion object {
    @JvmStatic
    @JvmName("createForGroups")
    fun createDisambiguatorForTargetGroups(
      groups: Set<TargetsToBuild>,
      anchors: TargetDisambiguationAnchors,
    ): TargetDisambiguator {
      // Note: This implementation does not take into account the dependency graph and it might be useful to do so.
      val unambiguousTargets = groups.asSequence().filter { !it.isAmbiguous() }.flatMap { it.targets }.toSet()
      val ambiguousTargets = groups.asSequence().filter { it.isAmbiguous() }.toSet()
      val allAnchors = anchors.anchorTargets + unambiguousTargets
      val disambiguated = ambiguousTargets
        .mapNotNull { it to (it.autoDisambiguate(allAnchors) ?: return@mapNotNull null) }
      val undefinedTargetSets = groups.asSequence().filter { it.requiresQueryDataRefresh() }.toSet()
      return TargetDisambiguator(
        unambiguousTargets = unambiguousTargets + disambiguated.map { it.second }.flatMap { it.targets },
        ambiguousTargetSets = ambiguousTargets - disambiguated.map { it.first },
        undefinedTargetSets = undefinedTargetSets,
      )
    }
  }
}

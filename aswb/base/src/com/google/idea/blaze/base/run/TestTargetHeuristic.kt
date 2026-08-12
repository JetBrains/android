/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run

import com.google.idea.blaze.base.dependencies.TargetInfo
import com.google.idea.blaze.base.dependencies.TestSize
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import java.io.File
import java.time.Instant

/** Heuristic to match test targets to source files. */
interface TestTargetHeuristic {

  companion object {
    @JvmField val EP_NAME = ExtensionPointName.create<TestTargetHeuristic>("com.google.idea.blaze.TestTargetHeuristic")

    /** Filters reachable test targets based on available heuristic extensions, returning the narrowed list of candidate targets. */
    @JvmStatic
    fun filterTargetsForSourceFile(
      project: Project,
      sourcePsiFile: PsiFile?,
      sourceFile: File,
      targets: Collection<TargetInfo>,
      testSize: TestSize?,
    ): List<TargetInfo> {
      if (targets.isEmpty()) {
        return emptyList()
      }
      var filteredTargets = targets.toList()
      for (filter in EP_NAME.extensions) {
        val matches = filteredTargets.filter { filter.matchesSource(project, it, sourcePsiFile, sourceFile, testSize) }
        if (matches.size == 1) {
          return matches
        }
        if (matches.isNotEmpty()) {
          // A higher-priority filter found more than one match -- subsequent filters will only consider these matches.
          filteredTargets = matches
        }
      }
      return filteredTargets
    }

    /**
     * Given a source file and all test rules reachable from that file, chooses a test rule based on available filters, falling back to
     * choosing the most recently synced one if there is no match.
     */
    @JvmStatic
    fun chooseTestTargetForSourceFile(
      project: Project,
      sourcePsiFile: PsiFile?,
      sourceFile: File,
      targets: Collection<TargetInfo>,
      testSize: TestSize?,
    ): TargetInfo? {
      val filteredTargets = filterTargetsForSourceFile(project, sourcePsiFile, sourceFile, targets, testSize)
      // finally order by syncTime (if available), returning the most recently synced
      return filteredTargets.maxByOrNull { it.syncTime() ?: Instant.EPOCH } ?: filteredTargets.firstOrNull()
    }
  }

  /** Returns true if the rule and source file match, according to this heuristic. */
  fun matchesSource(project: Project, target: TargetInfo, sourcePsiFile: PsiFile?, sourceFile: File, testSize: TestSize?): Boolean
}

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
package com.google.idea.blaze.qsync.project

import com.google.idea.blaze.common.Label
import java.util.ArrayDeque
import java.util.HashSet

/** Represents the status of a target in the dependency graph relative to the project. */
enum class TargetStatus {
  /** Target is not known to the project graph. */
  UNKNOWN,
  /** Target is within the project scope and supported by the IDE. */
  IN_PROJECT_SCOPE,
  /** Target is external to the project scope or must always be built. */
  EXTERNAL_TO_PROJECT_SCOPE,
}

/**
 * An abstraction for accessing targets in the dependency graph. Used to decouple the traversal algorithm from specific graph
 * implementations.
 */
interface DependencyGraphProvider {
  fun getTarget(label: Label): Target
}

/**
 * A node in the dependency graph used for required targets computation. Implementations must ensure proper equality based on the target
 * label.
 */
interface Target {
  val label: Label
  val dependencies: Collection<Target>
  val status: TargetStatus
}

/**
 * Computes the set of targets that are required to be built for the given [projectTargets]. These are targets that are either unknown or
 * external to the project scope.
 */
fun computeTargetsRequiredFor(projectTargets: Collection<Label>, provider: DependencyGraphProvider): Set<Target> {
  val seen = HashSet<Target>()
  val queue = ArrayDeque<Target>()

  // First map to targets
  for (label in projectTargets) {
    val target = provider.getTarget(label)
    if (seen.add(target)) queue.add(target)
  }

  val externalDeps = mutableSetOf<Target>()

  // Process targets
  while (!queue.isEmpty()) {
    val target = queue.removeFirst()

    // Use exhaustive when without redundant continue
    when (target.status) {
      TargetStatus.UNKNOWN,
      TargetStatus.EXTERNAL_TO_PROJECT_SCOPE -> {
        externalDeps.add(target)
      }
      TargetStatus.IN_PROJECT_SCOPE -> {
        for (depTarget in target.dependencies) {
          if (seen.add(depTarget)) {
            queue.add(depTarget)
          }
        }
      }
    }
  }
  return externalDeps
}

/** Extension function on RequestedTargets to compute required targets on demand. */
fun RequestedTargets.requiredTargets(provider: DependencyGraphProvider): Set<Label> {
  return computeTargetsRequiredFor(targetsToBuild, provider).map { it.label }.toSet()
}

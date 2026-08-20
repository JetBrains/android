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
package com.google.idea.blaze.qsync

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.qsync.project.PostQuerySyncData
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectStructureData
import java.nio.file.Path

/** Input parameters to a project refresh, and logic to determine what sort of refresh is required. */
class RefreshParameters(
  @JvmField val lastQuery: PostQuerySyncData,
  @JvmField val projectDefinition: ProjectDefinition,
  @JvmField val projectStructureData: ProjectStructureData,
  @JvmField val requestedPackages: Set<Path>,
  @JvmField val requireFullSync: (Context<*>) -> Boolean,
) {

  fun calculateAffectedPackages(): AffectedPackages {
    val projectStamps =
      projectStructureData.roots.asSequence().flatMap { it.buildPackages.values }.associate { pkg -> pkg.path to pkg.stamp }

    val lastQueryStamps =
      lastQuery
        .querySummary()
        .buildPackages
        .associateBy(keySelector = { it.packageLabel.getBuildPackagePath() }, valueTransform = { it.stamp })
    return calculatePackageStampAffectedPackages(
      latestProjectDataPackageStamp = projectStamps,
      latestBuildGraphDataPackageStamp = lastQueryStamps,
      packagesToUpdate = requestedPackages,
      projectScope = { projectDefinition.isIncluded(it) },
    )
  }
}

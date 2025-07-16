/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.npw.model

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.plugin.AgpVersions
import com.google.common.annotations.VisibleForTesting

/**
 * Picks the version of AGP to use for new projects and modules.
 *
 * For existing projects, FixedVersion is used, but for new projects, the version might be resolved
 * during template render, see [newProjectAgpVersionSelector]
 */
sealed class AgpVersionSelector {

  /** Resolve the version, calling the [publishedAgpVersions] supplier only if needed */
  abstract fun resolveVersion(publishedAgpVersions: () -> Set<AgpVersion>): AgpVersion

  /**
   * Returns true if the selector will select an AGP version of at least the version passed
   * irrespective of the published AGP versions.
   *
   * The only time [willSelectAtLeast]`(version)` will not be equivalent to
   * [resolveVersion]`(AgpVersions::getAvailableVersions)` is when differentiating between minor
   * versions is important and [newProjectAgpVersionSelector] returns a [MaximumPatchVersion]
   * selector. See AgoVersionSelectorTest for examples.
   */
  abstract fun willSelectAtLeast(version: AgpVersion): Boolean

  data class FixedVersion(private val version: AgpVersion) : AgpVersionSelector() {
    override fun resolveVersion(publishedAgpVersions: () -> Set<AgpVersion>): AgpVersion = version

    override fun willSelectAtLeast(minimum: AgpVersion): Boolean = this.version >= minimum
  }

  @VisibleForTesting
  data class MaximumPatchVersion(private val version: AgpVersion) : AgpVersionSelector() {
    override fun resolveVersion(publishedAgpVersions: () -> Set<AgpVersion>): AgpVersion {
      if (version.isPreview) return version
      return (publishedAgpVersions
        .invoke()
        .filter { it.major == version.major && it.minor == version.minor }
        .maxOrNull()
        ?.takeIf { it >= version }) ?: version
    }

    override fun willSelectAtLeast(minimum: AgpVersion): Boolean = this.version >= minimum
  }
}

/** Create a AgpVersionSelector for new project use */
fun newProjectAgpVersionSelector(): AgpVersionSelector {
  return if (StudioFlags.NPW_PICK_LATEST_PATCH_AGP.get()) {
    AgpVersionSelector.MaximumPatchVersion(AgpVersions.newProject)
  } else {
    AgpVersionSelector.FixedVersion(AgpVersions.newProject)
  }
}

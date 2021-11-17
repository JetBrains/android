/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.android.SdkConstants
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.FORCE
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.NO_UPGRADE
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.RECOMMEND

data class GradlePluginUpgradeState(
  val importance: Importance,
  val target: GradleVersion,
) {
  enum class Importance {
    NO_UPGRADE,
    RECOMMEND,
    FORCE,
  }
}

fun computeGradlePluginUpgradeState(
  current: GradleVersion,
  latestKnown: GradleVersion,
  published: Set<GradleVersion>
): GradlePluginUpgradeState {
  if (current >= latestKnown) return GradlePluginUpgradeState(NO_UPGRADE, current)
  GradleVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION).let { minimum ->
    if (current < minimum) {
      val earliestStable = published.filter { !it.isPreview }.filter { it >= minimum }.minOrNull() ?: latestKnown
      return GradlePluginUpgradeState(FORCE, earliestStable)
    }
  }

  if (!current.isPreview || current.previewType == "rc") {
    // If our latestKnown is stable, recommend it.
    if (!latestKnown.isPreview || latestKnown.previewType == "rc") return GradlePluginUpgradeState(RECOMMEND, latestKnown)
    // Otherwise, look for a newer published stable.
    val laterStable = published.filter { !it.isPreview }.filter { it > current }.maxOrNull()
                      ?: return GradlePluginUpgradeState(NO_UPGRADE, current)
    return GradlePluginUpgradeState(RECOMMEND, laterStable)
  }
  else if (current.previewType == "alpha" || current.previewType == "beta") {
    if (latestKnown.isSnapshot) {
      // If latestKnown is -dev and current is in the same series, leave it alone.
      if (latestKnown.compareIgnoringQualifiers(current) == 0) return GradlePluginUpgradeState(NO_UPGRADE, current)
      // If latestKnown is -dev and current is a preview from an earlier series, recommend an upgrade.
      return GradlePluginUpgradeState(RECOMMEND, latestKnown)
    }
    // In all other cases where latestKnown is later than an alpha or beta current, force an upgrade.
    return GradlePluginUpgradeState(FORCE, latestKnown)
  }
  else {
    // Current is a snapshot, probably -dev, and is less than latestKnown.  Force an upgrade to latestKnown.
    return GradlePluginUpgradeState(FORCE, latestKnown)
  }
}
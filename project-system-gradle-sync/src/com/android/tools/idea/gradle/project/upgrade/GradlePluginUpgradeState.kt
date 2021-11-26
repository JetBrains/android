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
import com.android.tools.idea.gradle.project.upgrade.ForcePluginUpgradeReason.MINIMUM
import com.android.tools.idea.gradle.project.upgrade.ForcePluginUpgradeReason.NO_FORCE
import com.android.tools.idea.gradle.project.upgrade.ForcePluginUpgradeReason.PREVIEW
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.FORCE
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.NO_UPGRADE
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.RECOMMEND

enum class ForcePluginUpgradeReason {
  NO_FORCE,
  MINIMUM,
  PREVIEW,
}

fun computeForcePluginUpgradeReason(current: GradleVersion, latestKnown: GradleVersion): ForcePluginUpgradeReason =
  when {
    // If the current is at least as new as latestKnown, no.
    current >= latestKnown -> NO_FORCE
    // If the current is lower than our minimum supported version, yes.
    current < GradleVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION) -> MINIMUM
    // If the current is a preview and the latest known is not a -dev version, yes.
    (current.previewType == "alpha" || current.previewType == "beta") && !latestKnown.isSnapshot -> PREVIEW
    // Otherwise, no.
    else -> NO_FORCE
  }

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
  when (computeForcePluginUpgradeReason(current, latestKnown)) {
    MINIMUM -> {
      val minimum = GradleVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION)
      val earliestStable = published.filter { !it.isPreview }.filter { it >= minimum }.minOrNull() ?: latestKnown
      return GradlePluginUpgradeState(FORCE, earliestStable)
    }
    // TODO(xof): in the cae of a -dev latestKnown and a preview from an earlier series, we should perhaps return the latest stable
    //  version from that series.  (During a -beta phase, there might not be any such version, though.)
    PREVIEW -> return GradlePluginUpgradeState(FORCE, latestKnown)
    NO_FORCE -> Unit
  }

  if (current >= latestKnown) return GradlePluginUpgradeState(NO_UPGRADE, current)
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
    throw IllegalStateException("Unreachable: handled by computeForcePluginUpgradeReason")
  }
  else {
    // Current is a snapshot, probably -dev, and is less than latestKnown.  Force an upgrade to latestKnown.
    return GradlePluginUpgradeState(FORCE, latestKnown)
  }
}
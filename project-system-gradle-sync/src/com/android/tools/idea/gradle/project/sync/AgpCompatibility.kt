/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.SdkConstants
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility
import com.android.tools.idea.gradle.project.upgrade.computeAndroidGradlePluginCompatibility

internal val MINIMUM_SUPPORTED_AGP_VERSION = AgpVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION)

internal val MODEL_CONSUMER_VERSION = ModelConsumerVersion(68, 1, "Android Studio Koala.1")

internal fun checkAgpVersionCompatibility(minimumModelConsumer: ModelConsumerVersion?, agpVersion: AgpVersion, flags: GradleSyncStudioFlags) {
  val latestKnown = AgpVersion.parse(flags.studioLatestKnownAgpVersion)

  /**
   * For AGPs that support minimumModelConsumerVersion, use that to determine the maximum supported,
   * otherwise fall back to [computeAndroidGradlePluginCompatibility]
   */
  if (minimumModelConsumer != null && flags.studioFlagSupportFutureAgpVersions) {
     return when {
      // TODO(b/272491108): Include the human readable minimum model consumer version (i.e the version of Studio to update to) in this error message
      agpVersion < MINIMUM_SUPPORTED_AGP_VERSION -> throw AgpVersionTooOld(agpVersion)
      (minimumModelConsumer > MODEL_CONSUMER_VERSION) -> throw AgpVersionTooNew(agpVersion, latestKnown)
      else -> Unit // Compatible
    }
  }

  return when (computeAndroidGradlePluginCompatibility(agpVersion, latestKnown)) {
    // We want to report to the user that they are using an AGP version that is below the minimum supported version for Android Studio,
    // and this is regardless of whether we want to trigger the upgrade assistant or not. Sync should always fail here.
    AndroidGradlePluginCompatibility.BEFORE_MINIMUM -> throw AgpVersionTooOld(agpVersion)
    AndroidGradlePluginCompatibility.DIFFERENT_PREVIEW ->
      if (!flags.studioFlagDisableForcedUpgrades) throw AgpVersionIncompatible(agpVersion, latestKnown) else Unit
    AndroidGradlePluginCompatibility.AFTER_MAXIMUM ->
      if (!flags.studioFlagDisableForcedUpgrades) throw AgpVersionTooNew(agpVersion, latestKnown) else Unit
    AndroidGradlePluginCompatibility.COMPATIBLE, AndroidGradlePluginCompatibility.DEPRECATED -> Unit
  }
}

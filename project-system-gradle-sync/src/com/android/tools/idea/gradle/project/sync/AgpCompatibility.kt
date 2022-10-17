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

import com.android.Version
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility
import com.android.tools.idea.gradle.project.upgrade.computeAndroidGradlePluginCompatibility

internal fun checkAgpVersionCompatibility(agpVersionString: String, syncOptions: SyncActionOptions) {
  val agpVersion = GradleVersion.parse(agpVersionString)
  val latestKnown = GradleVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION)
  when (computeAndroidGradlePluginCompatibility(agpVersion, latestKnown)) {
    // We want to report to the user that they are using an AGP version that is below the minimum supported version for Android Studio,
    // and this is regardless of whether we want to trigger the upgrade assistant or not. Sync should always fail here.
    AndroidGradlePluginCompatibility.BEFORE_MINIMUM -> throw AgpVersionTooOld(agpVersion)
    AndroidGradlePluginCompatibility.DIFFERENT_PREVIEW -> if (!syncOptions.flags.studioFlagDisableForcedUpgrades) throw AgpVersionIncompatible(agpVersion)
    AndroidGradlePluginCompatibility.AFTER_MAXIMUM -> if (!syncOptions.flags.studioFlagDisableForcedUpgrades) throw AgpVersionTooNew(agpVersion)
    AndroidGradlePluginCompatibility.COMPATIBLE -> Unit
  }
}

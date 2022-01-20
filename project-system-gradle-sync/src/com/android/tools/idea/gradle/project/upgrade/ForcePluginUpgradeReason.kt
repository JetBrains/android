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
    // If the current is a snapshot (and therefore of an earlier series than latestKnown), yes.
    current.isSnapshot -> PREVIEW
    // Otherwise, no.
    else -> NO_FORCE
  }

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
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.BEFORE_MINIMUM
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.COMPATIBLE
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.DIFFERENT_PREVIEW

enum class AndroidGradlePluginCompatibility {
  COMPATIBLE,
  BEFORE_MINIMUM,
  DIFFERENT_PREVIEW,
}

fun computeAndroidGradlePluginCompatibility(current: GradleVersion, latestKnown: GradleVersion): AndroidGradlePluginCompatibility =
  when {
    // If the current is at least as new as latestKnown, compatible.
    current >= latestKnown -> COMPATIBLE
    // If the current is lower than our minimum supported version, incompatible.
    current < GradleVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION) -> BEFORE_MINIMUM
    // If the current is a preview and the latest known is not a -dev version, incompatible.
    (current.previewType == "alpha" || current.previewType == "beta") && !latestKnown.isSnapshot -> DIFFERENT_PREVIEW
    // If the current is a snapshot (and therefore of an earlier series than latestKnown), incompatible.
    current.isSnapshot -> DIFFERENT_PREVIEW
    // Otherwise, compatible.
    else -> COMPATIBLE
  }

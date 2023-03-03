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
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.AFTER_MAXIMUM
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.BEFORE_MINIMUM
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.COMPATIBLE
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.DEPRECATED
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.DIFFERENT_PREVIEW

enum class AndroidGradlePluginCompatibility {
  COMPATIBLE,
  BEFORE_MINIMUM,
  DEPRECATED,
  DIFFERENT_PREVIEW,
  AFTER_MAXIMUM,
}


/**
 * AGP [current] and Studio's [latestKnown] can each be of "release class": alpha/beta, rc/release, or snapshot (-dev).
 * Their major/minor release number can be equal, or one major/minor release can precede the other.
 * If they are of the same major/minor and non-snapshot release class, then either can precede the other.  (All snapshots of the same
 * major/minor cycle compare equal, even if they reflect completely different actual code...)
 *
 * That gives 3 release classes * 2 distinct release classes * 3 major/minor orderings
 *          + 3 release classes * 2 major/minor orderings
 *          + 2 release classes * 1 major/minor equality * 2 patchlevel orderings
 *          ---------------------------------------------------------------------
 *          28 cases, not counting the exact equality case or the minimum supported version case
 *
 * Some of the complexity below is handling the fact that the "natural" version comparison places -dev between rc and release, whereas
 * we want to treat rc and release equivalently.
 *
 * Examples below are generated using as reference points:
 * - 7.1.0-alpha01, 7.1.0-rc01, 7.1.0-dev, with additional 7.1.0-alpha02, 7.1.0 when comparing within major/minor/release class
 * - 7.0.0-alpha01, 7.0.0-rc01, 7.0.0-dev
 * - 7.2.0-alpha01, 7.2.0-rc01, 7.2.0-dev
 *
 * There should be 9 cases with current: 7.1.0-alpha01, 9 with current: 7.1.0-rc01, 8 with current: 7.1.0-dev, one each of
 * current: 7.1.0-alpha02 and 7.1.0.
 *
 * There should be 3 cases with latestKnown: 7.0.0-alpha01, 3 with latestKnown: 7.0.0-rc01, 3 with latestKnown: 7.0.0-dev,
 * 3 cases with latestKnown: 7.2.0-alpha01, 3 with latestKnown: 7.2.0-rc01, 3 with latestKnown: 7.2.0-dev,
 * 3 cases with latestKnown: 7.1.0-alpha01, 3 with latestKnown: 7.1.0-rc01, 2 with latestKnown: 7.1.0-dev,
 * 1 with latestKnown: 7.1.0-alpha02, 1 with latestKnown: 7.1.0
 *
 * (Note that the above consistency check was written before the addition of the notion of the DEPRECATED class, which divides the
 * previous COMPATIBLE class in two: something is DEPRECATED if current is earlier than GRADLE_PLUGIN_NEXT_MINIMUM_VERSION and
 * latestKnown is after, the second of which conditions will always hold in production).
 */
fun computeAndroidGradlePluginCompatibility(current: AgpVersion, latestKnown: AgpVersion): AndroidGradlePluginCompatibility =
  run {
    val compatibleOrDeprecated = when {
      latestKnown < AgpVersion.parse(SdkConstants.GRADLE_PLUGIN_NEXT_MINIMUM_VERSION) -> COMPATIBLE
      current < AgpVersion.parse(SdkConstants.GRADLE_PLUGIN_NEXT_MINIMUM_VERSION) -> DEPRECATED
      else -> COMPATIBLE
    }
    when {
      // If the current and latestKnown are equal, compatible.
      // e.g. current = 7.1.0-alpha09, latestKnown = 7.1.0-alpha09
      current == latestKnown -> compatibleOrDeprecated // actually always compatible
      // If the current is lower than our minimum supported version, incompatible.
      // e.g. current = 3.1.0, latestKnown = 7.1.0-alpha09
      current < AgpVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION) -> BEFORE_MINIMUM
      // If the current/latestKnown are RC or releases, and of the same major/minor series, compatible. (2)
      // e.g. current = 7.1.0-rc01, latestKnown = 7.1.0
      //      current = 7.1.0, latestKnown = 7.1.0-rc01
      //      current = 7.1.1, latestKnown = 7.1.0
      (!latestKnown.isPreview || latestKnown.previewType == "rc") && (!current.isPreview || current.previewType == "rc") &&
      (AgpVersion(latestKnown.major, latestKnown.minor) == AgpVersion(current.major, current.minor)) ->
        compatibleOrDeprecated // in practice presumably always compatible
      // If the current is a snapshot and latestKnown is RC or release of the same major/minor series, incompatible. (1)
      // e.g. current = 7.1.0-dev, latestKnown = 7.1.0-rc01
      current.isSnapshot && (!latestKnown.isPreview || latestKnown.previewType == "rc") &&
      (AgpVersion(latestKnown.major, latestKnown.minor) == AgpVersion(current.major, current.minor)) -> DIFFERENT_PREVIEW
      // If the current is a snapshot and latestKnown is alpha/beta of the same major/minor series, compatible. (1)
      // e.g. current = 7.1.0-dev, latestKnown = 7.1.0-alpha01
      current.isSnapshot && (latestKnown.previewType == "alpha" || latestKnown.previewType == "beta") &&
      (AgpVersion(latestKnown.major, latestKnown.minor) == AgpVersion(current.major, current.minor)) ->
        compatibleOrDeprecated// in practice presumably always compatible
      // If the current is later than latestKnown, incompatible. (11)
      // e.g. current = 7.1.0-dev, latestKnown = 7.0.0-rc01
      //      current = 7.1.0-dev, latestKnown = 7.0.0-dev
      //      current = 7.1.0-dev, latestKnown = 7.0.0-alpha01
      //      current = 7.1.0-alpha01, latestKnown = 7.0.0-rc01
      //      current = 7.1.0-alpha01, latestKnown = 7.0.0-dev
      //      current = 7.1.0-alpha01, latestKnown = 7.0.0-alpha01
      //      current = 7.1.0-rc01, latestKnown = 7.0.0-rc01
      //      current = 7.1.0-rc01, latestKnown = 7.0.0-dev
      //      current = 7.1.0-rc01, latestKnown = 7.0.0-alpha01
      //      current = 7.1.0-rc01, latestKnown = 7.1.0-alpha01
      //      current = 7.1.0-alpha02, latestKnown = 7.1.0-alpha01
      current > latestKnown -> AFTER_MAXIMUM
      // If the current is a preview and the latest known is not a -dev version, incompatible. (4)
      // e.g. current = 7.1.0-alpha01, latestKnown = 7.1.0-rc01
      //      current = 7.1.0-alpha01, latestKnown = 7.1.0-alpha02
      //      current = 7.1.0-alpha01, latestKnown = 7.2.0-rc01
      //      current = 7.1.0-alpha01, latestKnown = 7.2.0-alpha01
      (current.previewType == "alpha" || current.previewType == "beta") && !latestKnown.isSnapshot -> DIFFERENT_PREVIEW
      // If the current is a snapshot (and therefore of an earlier series than latestKnown), incompatible. (3)
      // e.g. current = 7.1.0-dev, latestKnown = 7.2.0-rc01
      //      current = 7.1.0-dev, latestKnown = 7.2.0-alpha01
      //      current = 7.1.0-dev, latestKnown = 7.2.0-dev
      current.isSnapshot -> DIFFERENT_PREVIEW
      // Otherwise, compatible or deprecated. (6)
      // e.g. current = 7.1.0-rc01, latestKnown = 7.2.0-alpha01
      //      current = 7.1.0-rc01, latestKnown = 7.2.0-rc01
      //      current = 7.1.0-rc01, latestKnown = 7.2.0-dev
      //      current = 7.1.0-rc01, latestKnown = 7.1.0-dev
      //      current = 7.1.0-alpha01, latestKnown = 7.1.0-dev
      //      current = 7.1.0-alpha01, latestKnown = 7.2.0-dev
      else -> compatibleOrDeprecated
    }
  }
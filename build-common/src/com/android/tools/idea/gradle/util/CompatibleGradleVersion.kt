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
package com.android.tools.idea.gradle.util

import com.android.SdkConstants
import com.android.ide.common.repository.AgpVersion
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.VisibleForTesting

enum class CompatibleGradleVersion(val version: GradleVersion) {
  // Gradle versions earlier than 7.0.2 are not needed because Android Studio
  // supports AGP versions 7.0.0 and later, which require Gradle 7.0.2 or later.
  // As and when Android Studio drops support for AGP versions beyond 7.0.0, entries
  // from this table can be removed (though their presence is generally harmless
  // provided the computation of compatible Gradle versions, below, respects VERSION_MIN).
  VERSION_7_0_2(GradleVersion.version("7.0.2")),
  VERSION_7_2(GradleVersion.version("7.2")),
  VERSION_7_3_3(GradleVersion.version("7.3.3")),
  VERSION_7_4(GradleVersion.version("7.4")),
  VERSION_7_5(GradleVersion.version("7.5")),
  VERSION_8_0(GradleVersion.version("8.0")),
  VERSION_8_2(GradleVersion.version("8.2")),
  VERSION_8_4(GradleVersion.version("8.4")),
  VERSION_8_6(GradleVersion.version("8.6")),
  VERSION_8_7(GradleVersion.version("8.7")),
  VERSION_8_9(GradleVersion.version("8.9")),
  VERSION_8_10_2(GradleVersion.version("8.10.2")),
  VERSION_8_11_1(GradleVersion.version("8.11.1")),
  VERSION_8_13(GradleVersion.version("8.13")),
  VERSION_9_1_0(GradleVersion.version("9.1.0")),
  VERSION_9_3_1(GradleVersion.version("9.3.1")),
  VERSION_9_4_1(GradleVersion.version("9.4.1")),
  VERSION_9_5_0(GradleVersion.version("9.5.0")),
  VERSION_FOR_DEV(GradleVersion.version(SdkConstants.GRADLE_LATEST_VERSION));

  companion object {
    @VisibleForTesting
    val AGP_MAJOR_MINOR_TO_GRADLE_MAP =
      mapOf(
        AgpVersion.parse("7.0.0") to VERSION_7_0_2,
        AgpVersion.parse("7.1.0") to VERSION_7_2,
        AgpVersion.parse("7.2.0") to VERSION_7_3_3,
        AgpVersion.parse("7.3.0") to VERSION_7_4,
        AgpVersion.parse("7.4.0") to VERSION_7_5,
        AgpVersion.parse("8.0.0") to VERSION_8_0,
        AgpVersion.parse("8.1.0") to VERSION_8_0,
        AgpVersion.parse("8.2.0") to VERSION_8_2,
        AgpVersion.parse("8.3.0") to VERSION_8_4,
        AgpVersion.parse("8.4.0") to VERSION_8_6,
        AgpVersion.parse("8.5.0") to VERSION_8_7,
        AgpVersion.parse("8.6.0") to VERSION_8_7,
        AgpVersion.parse("8.7.0") to VERSION_8_9,
        AgpVersion.parse("8.8.0") to VERSION_8_10_2,
        AgpVersion.parse("8.9.0") to VERSION_8_11_1,
        AgpVersion.parse("8.10.0") to VERSION_8_11_1,
        AgpVersion.parse("8.11.0") to VERSION_8_13,
        AgpVersion.parse("8.12.0") to VERSION_8_13,
        AgpVersion.parse("8.13.0") to VERSION_8_13,
        AgpVersion.parse("9.0.0") to VERSION_9_1_0,
        AgpVersion.parse("9.1.0") to VERSION_9_3_1,
        AgpVersion.parse("9.2.0") to VERSION_9_4_1,
        AgpVersion.parse("9.3.0") to VERSION_9_5_0,
        AgpVersion.parse("9.4.0") to VERSION_FOR_DEV,
      )

    private fun getAssociatedGradleVersion(agpVersion: AgpVersion): CompatibleGradleVersion {
      val agpVersionMajorMinor = AgpVersion(agpVersion.major, agpVersion.minor)
      return when {
        agpVersionMajorMinor < AgpVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_FORCED_UPGRADE_VERSION) -> VERSION_MIN
        else -> AGP_MAJOR_MINOR_TO_GRADLE_MAP.getOrDefault(agpVersionMajorMinor, VERSION_FOR_DEV)
      }
    }

    private val VERSION_MIN = getAssociatedGradleVersion(AgpVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_FORCED_UPGRADE_VERSION))

    fun getCompatibleGradleVersion(agpVersion: AgpVersion): CompatibleGradleVersion {
      val compatibleGradleVersion = getAssociatedGradleVersion(agpVersion)
      return when {
        compatibleGradleVersion.version < VERSION_MIN.version -> VERSION_MIN
        else -> compatibleGradleVersion
      }
    }
  }
}

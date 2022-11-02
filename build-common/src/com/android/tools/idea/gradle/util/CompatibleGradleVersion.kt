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
import com.android.ide.common.repository.GradleVersion

enum class CompatibleGradleVersion(val version: GradleVersion) {
  // Gradle versions earlier than 4.4 are not needed because Android Studio
  // supports AGP versions 3.1.0 and later, which require Gradle 4.4 or later.
  // As and when Android Studio drops support for AGP versions beyond 3.1.0, entries
  // from this table can be removed (though their presence is generally harmless
  // provided the computation of compatible Gradle versions, below, respects VERSION_MIN).
  VERSION_4_4(GradleVersion.parse("4.4")),
  VERSION_4_6(GradleVersion.parse("4.6")),
  VERSION_MIN(GradleVersion.parse(SdkConstants.GRADLE_MINIMUM_VERSION)),
  VERSION_4_10_1(GradleVersion.parse("4.10.1")),
  VERSION_5_1_1(GradleVersion.parse("5.1.1")),
  VERSION_5_4_1(GradleVersion.parse("5.4.1")),
  VERSION_5_6_4(GradleVersion.parse("5.6.4")),
  VERSION_6_1_1(GradleVersion.parse("6.1.1")),
  VERSION_6_5(GradleVersion.parse("6.5")),
  VERSION_6_7_1(GradleVersion.parse("6.7.1")),
  VERSION_7_0_2(GradleVersion.parse("7.0.2")),
  VERSION_7_2(GradleVersion.parse("7.2")),
  VERSION_7_3_3(GradleVersion.parse("7.3.3")),
  VERSION_7_4(GradleVersion.parse("7.4")),
  VERSION_FOR_DEV(GradleVersion.parse(SdkConstants.GRADLE_LATEST_VERSION)),

  ;

  companion object {
    fun getCompatibleGradleVersion(agpVersion: AgpVersion): CompatibleGradleVersion {
      val agpVersionMajorMinor = GradleVersion(agpVersion.major, agpVersion.minor)
      val compatibleGradleVersion = when {
        GradleVersion.parse("3.1") >= agpVersionMajorMinor -> VERSION_4_4
        GradleVersion.parse("3.2") >= agpVersionMajorMinor -> VERSION_4_6
        GradleVersion.parse("3.3") >= agpVersionMajorMinor -> VERSION_4_10_1
        GradleVersion.parse("3.4") >= agpVersionMajorMinor -> VERSION_5_1_1
        GradleVersion.parse("3.5") >= agpVersionMajorMinor -> VERSION_5_4_1
        GradleVersion.parse("3.6") >= agpVersionMajorMinor -> VERSION_5_6_4
        GradleVersion.parse("4.0") >= agpVersionMajorMinor -> VERSION_6_1_1
        GradleVersion.parse("4.1") >= agpVersionMajorMinor -> VERSION_6_5
        GradleVersion.parse("4.2") >= agpVersionMajorMinor -> VERSION_6_7_1
        GradleVersion.parse("7.0") >= agpVersionMajorMinor -> VERSION_7_0_2
        GradleVersion.parse("7.1") >= agpVersionMajorMinor -> VERSION_7_2
        GradleVersion.parse("7.2") >= agpVersionMajorMinor -> VERSION_7_3_3
        GradleVersion.parse("7.3") >= agpVersionMajorMinor -> VERSION_7_4
        else -> VERSION_FOR_DEV
      }
      return when {
        compatibleGradleVersion.version < VERSION_MIN.version -> VERSION_MIN
        else -> compatibleGradleVersion
      }
    }
  }
}
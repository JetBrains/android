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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.upgrade.ForcePluginUpgradeReason.MAXIMUM
import com.android.tools.idea.gradle.project.upgrade.ForcePluginUpgradeReason.MINIMUM
import com.android.tools.idea.gradle.project.upgrade.ForcePluginUpgradeReason.NO_FORCE
import com.android.tools.idea.gradle.project.upgrade.ForcePluginUpgradeReason.PREVIEW
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ComputeForcePluginUpgradeReasonTest(private val info: TestInfo) {

  @Test
  fun testComputedCompatibility() {
    val computed = computeForcePluginUpgradeReason(info.projectVersion, info.studioVersion)
    assertEquals("Compatibility for project version ${info.projectVersion} and Studio version ${info.studioVersion}",
                 info.expectedCompatibility, computed)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data() = listOf(
      // Special case 1: versions equal
      TestInfo("4.2.0-alpha01", "4.2.0-alpha01", NO_FORCE),

      // Special case 2: current version earlier than minimum supported
      TestInfo("3.1.0", "4.2.0-alpha01", MINIMUM),

      // RC/release of the same major/minor cycle
      TestInfo("7.1.0-rc01", "7.1.0", NO_FORCE),
      TestInfo("7.1.0", "7.1.0-rc01", NO_FORCE),

      // project's version is a snapshot, and Studio is a RC/release of the same major/minor version
      TestInfo("7.1.0-dev", "7.1.0-rc01", PREVIEW),

      // project's version is a snapshot, and Studio is an alpha/beta of the same major/minor version
      TestInfo("7.1.0-dev", "7.1.0-alpha01", NO_FORCE),

      // project's version is later than Studio's latest
      TestInfo("7.1.0-dev", "7.0.0-rc01", MAXIMUM),
      TestInfo("7.1.0-dev", "7.0.0-dev", MAXIMUM),
      TestInfo("7.1.0-dev", "7.0.0-alpha01", MAXIMUM),
      TestInfo("7.1.0-alpha01", "7.0.0-rc01", MAXIMUM),
      TestInfo("7.1.0-alpha01", "7.0.0-dev", MAXIMUM),
      TestInfo("7.1.0-alpha01", "7.0.0-alpha01", MAXIMUM),
      TestInfo("7.1.0-rc01", "7.0.0-rc01", MAXIMUM),
      TestInfo("7.1.0-rc01", "7.0.0-dev", MAXIMUM),
      TestInfo("7.1.0-rc01", "7.0.0-alpha01", MAXIMUM),
      TestInfo("7.1.0-rc01", "7.1.0-alpha01", MAXIMUM),
      TestInfo("7.1.0-alpha02", "7.1.0-alpha01", MAXIMUM),

      // project's version is an alpha/beta and Studio's version is not a snapshot
      TestInfo("7.1.0-alpha01", "7.1.0-rc01", PREVIEW),
      TestInfo("7.1.0-alpha01", "7.1.0-alpha02", PREVIEW),
      TestInfo("7.1.0-alpha01", "7.2.0-alpha01", PREVIEW),
      TestInfo("7.1.0-alpha01", "7.2.0-rc01", PREVIEW),

      // project's version is a snapshot of an earlier series
      TestInfo("7.1.0-dev", "7.2.0-dev", PREVIEW),
      TestInfo("7.1.0-dev", "7.2.0-alpha01", PREVIEW),
      TestInfo("7.1.0-dev", "7.2.0-rc01", PREVIEW),

      // otherwise
      TestInfo("7.1.0-rc01", "7.2.0-alpha01", NO_FORCE),
      TestInfo("7.1.0-rc01", "7.2.0-rc01", NO_FORCE),
      TestInfo("7.1.0-rc01", "7.2.0-dev", NO_FORCE),
      TestInfo("7.1.0-rc01", "7.1.0-dev", NO_FORCE),
      TestInfo("7.1.0-alpha01", "7.1.0-dev", NO_FORCE),
      TestInfo("7.1.0-alpha01", "7.2.0-dev", NO_FORCE),
    )
  }

  data class TestInfo(
    val projectVersion: GradleVersion,
    val studioVersion: GradleVersion,
    val expectedCompatibility: ForcePluginUpgradeReason,
  ) {
    constructor(projectVersion: String, studioVersion: String, expectedCompatibility: ForcePluginUpgradeReason):
      this(GradleVersion.parse(projectVersion), GradleVersion.parse(studioVersion), expectedCompatibility)
  }
}
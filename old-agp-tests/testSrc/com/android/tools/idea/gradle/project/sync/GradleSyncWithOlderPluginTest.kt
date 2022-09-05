/*
 * Copyright (C) 2019 The Android Open Source Project
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
@file:Suppress("UnstableApiUsage")

package com.android.tools.idea.gradle.project.sync

import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_33
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_33_WITH_5_3_1
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.requestSyncAndWait
import com.google.common.truth.Truth
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServices
import org.junit.Rule
import org.junit.Test

/**
 * Integration test for Gradle Sync with old versions of Android plugin.
 */
@RunsInEdt
@OldAgpTest(agpVersions = ["3.3.2"], gradleVersions = ["5.3.1"])
class GradleSyncWithOlderPluginTest {
  @get:Rule
  var projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  /**
   * Verify that Gradle daemons can be stopped for Gradle 5.3.1 (b/155991417).
   */
  @Test
  fun testDaemonStops5Dot3Dot1() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AGP_33_WITH_5_3_1)
    preparedProject.open { project ->
      verifyDaemonStops(project)
    }
  }

  fun verifyDaemonStops(project: Project) {
    GradleDaemonServices.stopDaemons()
    Truth.assertThat(areGradleDaemonsRunning()).isFalse()
    project.requestSyncAndWait()
    Truth.assertThat(areGradleDaemonsRunning()).isTrue()
    GradleDaemonServices.stopDaemons()
    Truth.assertThat(areGradleDaemonsRunning()).isFalse()
  }
}

private fun areGradleDaemonsRunning(): Boolean {
  val daemonStatus = GradleDaemonServices.getDaemonsStatus()
  for (status in daemonStatus) {
    if (!StringUtil.equalsIgnoreCase(status.status, "stopped")) {
      return true
    }
  }
  return false
}

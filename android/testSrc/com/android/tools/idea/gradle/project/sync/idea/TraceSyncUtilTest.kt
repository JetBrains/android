/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.tools.idea.flags.ExperimentalSettingsConfigurable.TraceProfileItem.DEFAULT
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import org.junit.Test
import java.io.File

class TraceSyncUtilTest : HeavyPlatformTestCase() {
  private lateinit var settings: GradleExperimentalSettings

  override fun setUp() {
    super.setUp()
    settings = IdeComponents(project).mockApplicationService(GradleExperimentalSettings::class.java)
  }

  @Test
  fun testAddTraceJvmArgsWithTraceDisabled() {
    settings.TRACE_GRADLE_SYNC = false
    val jvmArgs = mutableListOf<Pair<String, String>>()
    // Invoke method to test.
    addTraceJvmArgs(jvmArgs)
    assertThat(jvmArgs).isEmpty()
  }

  @Test
  fun testAddTraceJvmArgsWithTraceEnabled() {
    settings.TRACE_GRADLE_SYNC = true
    settings.TRACE_PROFILE_SELECTION = DEFAULT
    val jvmArgs = mutableListOf<Pair<String, String>>()
    // Invoke method to test.
    addTraceJvmArgs(jvmArgs)

    val agentJar = findAgentJar()
    assertThat(jvmArgs).hasSize(1)
    assertThat(jvmArgs[0].first).isEqualTo("-javaagent:${agentJar}")
    assertThat(jvmArgs[0].second).endsWith(".profile")
  }

  @Test
  fun testCreateTraceProfileFile() {
    val traceMethods = "Trace: com.android.build.gradle.internal.ide.ModelBuilder\n" +
                       "Trace: com.android.build.gradle.internal.ide.dependencies.ArtifactDependencyGraph\n"

    val profile = File(createTraceProfileFile(traceMethods))
    // Verify that profile is created.
    assertTrue(profile.isFile)
    // Verify that the profile starts with Output line, and ends with traceMethods.
    val text = profile.readText()
    val outputFilePrefix = FileUtil.toSystemDependentName(File(PathManager.getLogPath(), "sync_profile_report_").absolutePath)
    assertThat(text).startsWith("Output: ${outputFilePrefix}")
    assertThat(text).endsWith(traceMethods)
  }

  @Test
  fun testFindAgentJar() {
    val agentJar = findAgentJar()
    assertThat(agentJar).endsWith("trace_agent.jar")
  }
}
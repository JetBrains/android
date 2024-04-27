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

import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.GradleExperimentalSettingsConfigurable.TraceProfileItem.DEFAULT
import com.android.tools.idea.gradle.project.sync.idea.TraceSyncUtil.createTraceProfileFile
import com.android.tools.idea.gradle.project.sync.idea.TraceSyncUtil.findAgentJar
import com.android.tools.idea.gradle.project.sync.idea.TraceSyncUtil.updateTraceArgsInFile
import com.google.common.truth.Truth.assertThat
import com.intellij.diagnostic.VMOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.rules.TempDirectory
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class TraceSyncUtilTest : BareTestFixtureTestCase() {
  @JvmField @Rule val tempDir = TempDirectory()

  private lateinit var settings: GradleExperimentalSettings
  private lateinit var vmFile: Path

  @Before
  fun setUp() {
    settings = mock(GradleExperimentalSettings::class.java)
    ApplicationManager.getApplication().replaceService(GradleExperimentalSettings::class.java, settings, testRootDisposable)
    vmFile = tempDir.newFile("studio.vmoptions").toPath()
    System.setProperty("jb.vmOptionsFile", vmFile.toString())
  }

  @After
  fun tearDown() {
    System.clearProperty("jb.vmOptionsFile")
  }

  @Test
  fun testAddTraceJvmArgsWithTraceDisabled() {
    settings.TRACE_GRADLE_SYNC = false
    val agentJar = findAgentJar()
    val originalOptions = listOf("-Xms256m", "-Xmx1280m", "-Djava.net.preferIPv4Stack=true")
    Files.write(vmFile, originalOptions + "-javaagent:${agentJar}=/tmp/text.profile", VMOptions.getFileCharset())

    // Invoke method to test.
    updateTraceArgsInFile()

    // Verify that -javaagent line is removed.
    val updatedOptions = Files.readAllLines(vmFile, VMOptions.getFileCharset())
    assertThat(updatedOptions).isEqualTo(originalOptions)
  }

  @Test
  fun testAddTraceJvmArgsWithTraceEnabled() {
    settings.TRACE_GRADLE_SYNC = true
    settings.TRACE_PROFILE_SELECTION = DEFAULT
    val agentJar = findAgentJar()
    val originalOptions = listOf("-Xms256m", "-Xmx1280m", "-Djava.net.preferIPv4Stack=true")
    Files.write(vmFile, originalOptions, VMOptions.getFileCharset())

    // Invoke method to test.
    updateTraceArgsInFile()

    // Verify that -javaagent line is added.
    val updatedOptions = Files.readAllLines(vmFile, VMOptions.getFileCharset()).joinToString("\n")
    assertThat(updatedOptions).startsWith((originalOptions + "-javaagent:${agentJar}=").joinToString("\n"))
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
    val outputFilePrefix = FileUtilRt.toSystemDependentName(File(PathManager.getLogPath(), "sync_profile_report_").absolutePath)
    assertThat(text).startsWith("Output: ${outputFilePrefix}")
    assertThat(text).endsWith(traceMethods)
  }

  @Test
  fun testFindAgentJar() {
    val agentJar = findAgentJar()
    assertThat(agentJar).endsWith("trace_agent.jar")
  }
}
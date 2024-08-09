/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.builder.model.SyncIssue.Companion.TYPE_MISSING_COMPOSE_COMPILER_GRADLE_PLUGIN
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.hyperlink.AddComposeCompilerGradlePluginHyperlink
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Tests for [MissingComposeCompilerGradlePluginReporter]
 */
class MissingComposeCompilerGradlePluginReporterTest {
  @Test
  fun testSupportedIssueType() {
    val reporter = MissingComposeCompilerGradlePluginReporter()
    assertThat(reporter.supportedIssueType).isEqualTo(TYPE_MISSING_COMPOSE_COMPILER_GRADLE_PLUGIN)
  }

  @Test
  fun testQuickFixes() {
    val reporter = MissingComposeCompilerGradlePluginReporter()
    val mockProject = mock(Project::class.java)
    val mockModule = mock(Module::class.java)
    val mockIdeSyncIssue = mock(IdeSyncIssue::class.java)
    whenever(mockIdeSyncIssue.data).thenReturn("2.0.0")
    val quickfixes =
      reporter.getCustomLinks(
        mockProject,
        listOf(mockIdeSyncIssue),
        listOf(mockModule),
        buildFileMap = mapOf()
      )
    assertThat(quickfixes).hasSize(1)
    assertThat(quickfixes[0]).isInstanceOf(AddComposeCompilerGradlePluginHyperlink::class.java)
  }

  @Test
  fun testNoQuickFixesWhenNoAffectedModules() {
    val reporter = MissingComposeCompilerGradlePluginReporter()
    val mockProject = mock(Project::class.java)
    val mockIdeSyncIssue = mock(IdeSyncIssue::class.java)
    whenever(mockIdeSyncIssue.data).thenReturn("2.0.0")
    val quickfixes =
      reporter.getCustomLinks(
        mockProject,
        listOf(mockIdeSyncIssue),
        affectedModules = listOf(),
        buildFileMap = mapOf()
      )
    assertThat(quickfixes).hasSize(0)
  }

  @Test
  fun testNoQuickFixesWhenNoKotlinVersion() {
    val reporter = MissingComposeCompilerGradlePluginReporter()
    val mockProject = mock(Project::class.java)
    val mockModule = mock(Module::class.java)
    val mockIdeSyncIssue = mock(IdeSyncIssue::class.java)
    whenever(mockIdeSyncIssue.data).thenReturn(null)
    val quickfixes =
      reporter.getCustomLinks(
        mockProject,
        listOf(mockIdeSyncIssue),
        listOf(mockModule),
        buildFileMap = mapOf()
      )
    assertThat(quickfixes).hasSize(0)
  }

  @Test
  fun testNoQuickFixesWhenMultipleKotlinVersions() {
    val reporter = MissingComposeCompilerGradlePluginReporter()
    val mockProject = mock(Project::class.java)
    val mockModule = mock(Module::class.java)
    val mockIdeSyncIssue1 = mock(IdeSyncIssue::class.java)
    val mockIdeSyncIssue2 = mock(IdeSyncIssue::class.java)
    whenever(mockIdeSyncIssue1.data).thenReturn("1.9.20")
    whenever(mockIdeSyncIssue2.data).thenReturn("2.0.0")
    val quickfixes =
      reporter.getCustomLinks(
        mockProject,
        listOf(mockIdeSyncIssue1, mockIdeSyncIssue2),
        listOf(mockModule),
        buildFileMap = mapOf()
      )
    assertThat(quickfixes).hasSize(0)
  }
}
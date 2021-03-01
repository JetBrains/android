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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.builder.model.SyncIssue.Companion.TYPE_JCENTER_IS_DEPRECATED
import com.android.tools.idea.gradle.project.sync.hyperlink.RemoveJcenterHyperlink
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Tests for [JcenterDeprecatedReporter]
 */
class JcenterDeprecatedReporterTest {
  @Test
  fun `expected type`() {
    val reporter = JcenterDeprecatedReporter()
    assertThat(reporter.supportedIssueType).isEqualTo(TYPE_JCENTER_IS_DEPRECATED)
  }

  @Test
  fun `quick fixes when initialized and can be applied`() {
    val quickfixes = generateQuickfixes(initialized = true, canApply = true)
    assertThat(quickfixes).hasSize(1)
    assertThat(quickfixes[0]).isInstanceOf(RemoveJcenterHyperlink::class.java)
  }

  @Test
  fun `quick fixes when initialized and cannot be applied`() {
    val quickfixes = generateQuickfixes(initialized = true, canApply = false)
    assertThat(quickfixes).isEmpty()
  }

  @Test
  fun `quick fixes when not initialized and can be applied`() {
    val quickfixes = generateQuickfixes(initialized = false, canApply = true)
    assertThat(quickfixes).isEmpty()
  }

  @Test
  fun `quick fixes when not initialized and cannot be applied`() {
    val quickfixes = generateQuickfixes(initialized = false, canApply = false)
    assertThat(quickfixes).isEmpty()
  }

  private fun generateQuickfixes(initialized: Boolean, canApply: Boolean): MutableList<NotificationHyperlink> {
    val reporter = JcenterDeprecatedReporter()
    val mockProject = mock(Project::class.java)
    `when`(mockProject.isInitialized).thenReturn(initialized)
    val modules = ArrayList<Module>()
    return reporter.createQuickFixes(mockProject, modules) { _, _ -> canApply }
  }
}
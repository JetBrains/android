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

import com.android.builder.model.SyncIssue
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenGradleSettingsHyperlink
import com.google.common.truth.Truth
import org.junit.Test

/**
 * Tests for [AgpUsedJavaTooLowReporter]
 */
class AgpUsedJavaTooLowReporterTest {
  @Test
  fun `expected type`() {
    val reporter = AgpUsedJavaTooLowReporter()
    Truth.assertThat(reporter.supportedIssueType).isEqualTo(SyncIssue.TYPE_AGP_USED_JAVA_VERSION_TOO_LOW)
  }

  @Test
  fun `quick fixes contains open Gradle Settings only`() {
    val reporter = AgpUsedJavaTooLowReporter()
    val quickfixes = reporter.createQuickFixes()
    Truth.assertThat(quickfixes).hasSize(1)
    Truth.assertThat(quickfixes[0]).isInstanceOf(OpenGradleSettingsHyperlink::class.java)
  }
}
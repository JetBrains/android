/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.lint

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.android.tools.idea.gservices.DevServicesDeprecationDataProvider
import com.android.tools.idea.gservices.DevServicesDeprecationStatus
import com.android.tools.idea.lint.common.LintIgnoredResult
import com.android.tools.idea.testing.disposable
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.TextFormat
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AndroidLintPlayPolicyDeprecationTest {

  @get:Rule val projectRule = ProjectRule()

  private val unsupportedData: DevServicesDeprecationData =
    DevServicesDeprecationData(
      header = "",
      description = "my description",
      moreInfoUrl = "link",
      showUpdateAction = true,
      status = DevServicesDeprecationStatus.UNSUPPORTED,
    )

  private lateinit var client: AndroidLintIdeClient
  private lateinit var mockDeprecationService: DevServicesDeprecationDataProvider

  @Test
  fun testDeprecationMessage() {
    StudioFlags.ENABLE_PLAY_POLICY_INSIGHTS.override(true)
    mockDeprecationService = mock()
    doAnswer { unsupportedData }
      .whenever(mockDeprecationService)
      .getCurrentDeprecationData(any(), any())
    ApplicationManager.getApplication()
      .replaceService(
        DevServicesDeprecationDataProvider::class.java,
        mockDeprecationService,
        projectRule.disposable,
      )
    val incident = Incident()
    val issue = mock<Issue>()
    doReturn(Category(null, "Play Policy", 100)).whenever(issue).category
    incident.issue = issue

    client =
      object : AndroidLintIdeClient(projectRule.project, LintIgnoredResult()) {
        override fun report(context: Context, incident: Incident, format: TextFormat) {
          super.report(context, incident, format)
          assertThat(incident.message).contains(DEPRECATION_PREFIX)
        }
      }
    client.report(mock<Context>(), incident, TextFormat.TEXT)
    StudioFlags.ENABLE_PLAY_POLICY_INSIGHTS.clearOverride()
  }
}

/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.insights.persistence.AppInsightsSettings
import com.android.tools.idea.sdk.AndroidEnvironmentChecker
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl.MockToolWindow
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class AppInsightsToolWindowFactoryTest {
  @get:Rule val projectRule = ProjectRule()

  @Test
  fun `tab selection is persisted`() {
    val factory = AppInsightsToolWindowFactory()
    val toolWindow = MockToolWindow(projectRule.project)
    factory.createTabs(projectRule.project, toolWindow)

    toolWindow.contentManager.setSelectedContent(toolWindow.contentManager.getContent(1)!!)
    assertThat(projectRule.project.service<AppInsightsSettings>().selectedTabId).isEqualTo("Test 2")

    toolWindow.contentManager.setSelectedContent(toolWindow.contentManager.getContent(0)!!)
    assertThat(projectRule.project.service<AppInsightsSettings>().selectedTabId).isEqualTo("Test 1")
  }

  @Test
  fun `tab selection is restored from setting when tabs are created`() {
    val factory = AppInsightsToolWindowFactory()
    val toolWindow = MockToolWindow(projectRule.project)

    projectRule.project.service<AppInsightsSettings>().selectedTabId = "Test 2"
    factory.createTabs(projectRule.project, toolWindow)

    assertThat(toolWindow.contentManager.selectedContent)
      .isEqualTo(toolWindow.contentManager.contents.find { it.tabName == "Test 2" })
  }

  @Test
  fun isLibraryToolWindow() {
    val toolWindow =
      LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensions.find {
        it.id == "App Quality Insights"
      } ?: throw AssertionError("Tool window not found")

    assertThat(toolWindow.librarySearchClass)
      .isEqualTo(AndroidEnvironmentChecker::class.qualifiedName)
  }
}

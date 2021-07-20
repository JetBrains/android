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
package com.android.tools.adtui.toolwindow.splittingtabs.actions

import com.android.testutils.MockitoKt
import com.android.tools.adtui.toolwindow.splittingtabs.setIsSplittingTab
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.content.Content
import org.junit.Rule
import org.junit.Test
import javax.swing.JPanel

class SplittingTabsContextMenuActionTest {
  @get:Rule
  val appRule = ApplicationRule()

  private val project = MockitoKt.mock<Project>()
  private val splittingTabsContextMenuAction = TestSplittingTabsContextMenuAction("")
  private val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
  private val event by lazy { TestActionEvent({ }, splittingTabsContextMenuAction) }
  private val content by lazy {
    toolWindow.contentManager.factory.createContent(JPanel(), "Content", false).also(toolWindow.contentManager::addContent)
  }

  @Test
  fun presentationTextSet() {
    val splittingTabsContextMenuAction = TestSplittingTabsContextMenuAction("Text")

    assertThat(splittingTabsContextMenuAction.templatePresentation.text).isEqualTo("Text")
  }

  @Test
  fun update_nullContent_invisibleAndDisabled() {
    splittingTabsContextMenuAction.update(event, toolWindow, null)

    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun update_notSplittingTabContent_invisibleAndDisabled() {
    splittingTabsContextMenuAction.update(event, toolWindow, content)

    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(splittingTabsContextMenuAction.isEnabledCalled).isEqualTo(0)
  }

  @Test
  fun update_splittingTabContent_visibleAndEnabled() {
    content.setIsSplittingTab()

    splittingTabsContextMenuAction.update(event, toolWindow, content)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(splittingTabsContextMenuAction.isEnabledCalled).isEqualTo(1)
  }

  @Test
  fun actionPerformed_notSplittingTabContent_doesNotPerformAction() {
    splittingTabsContextMenuAction.actionPerformed(event, toolWindow, content)

    assertThat(splittingTabsContextMenuAction.actionPerformedCalled).isEqualTo(0)
  }

  @Test
  fun actionPerformed_nullContent_doesNotPerformAction() {
    splittingTabsContextMenuAction.actionPerformed(event, toolWindow, null)

    assertThat(splittingTabsContextMenuAction.actionPerformedCalled).isEqualTo(0)
  }

  @Test
  fun actionPerformed_nullContentManager_doesNotPerformAction() {
    // A content that hasn't been added has a null manager.
    val content = toolWindow.contentManager.factory.createContent(JPanel(), "Content", false)
    content.setIsSplittingTab()

    splittingTabsContextMenuAction.actionPerformed(event, toolWindow, content)

    assertThat(splittingTabsContextMenuAction.actionPerformedCalled).isEqualTo(0)
  }

  @Test
  fun actionPerformed_splittingTabContent_performsAction() {
    content.setIsSplittingTab()

    splittingTabsContextMenuAction.actionPerformed(event, toolWindow, content)

    assertThat(splittingTabsContextMenuAction.actionPerformedCalled).isEqualTo(1)
  }

  private class TestSplittingTabsContextMenuAction(text: String) : SplittingTabsContextMenuAction(text) {
    var isEnabledCalled = 0
    var actionPerformedCalled = 0

    override fun isEnabled(content: Content): Boolean {
      isEnabledCalled++
      return true
    }

    override fun actionPerformed(content: Content) {
      actionPerformedCalled++
    }
  }
}
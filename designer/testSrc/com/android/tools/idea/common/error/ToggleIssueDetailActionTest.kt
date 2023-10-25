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
package com.android.tools.idea.common.error

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.TestToolWindowManager
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.TestActionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ToggleIssueDetailActionTest {
  @JvmField @Rule val rule = AndroidProjectRule.inMemory()

  private lateinit var toolWindow: ToolWindow
  private lateinit var service: IssuePanelService

  @Before
  fun setup() {
    rule.replaceProjectService(ToolWindowManager::class.java, TestToolWindowManager(rule.project))
    val manager = ToolWindowManager.getInstance(rule.project)
    toolWindow = manager.registerToolWindow(RegisterToolWindowTask(ProblemsView.ID))
    val contentManager = toolWindow.contentManager
    val content =
      contentManager.factory.createContent(mock(), "Current File", true).apply {
        isCloseable = false
      }
    contentManager.addContent(content)
    contentManager.setSelectedContent(content)

    invokeAndWaitIfNeeded {
      service = IssuePanelService.getInstance(rule.project)
      service.initIssueTabs(toolWindow)
    }
  }

  @Test
  fun testUpdate() {
    val action = ToggleIssueDetailAction()

    service.setSharedIssuePanelVisibility(false)
    TestActionEvent.createTestEvent {
        if (PlatformDataKeys.PROJECT.`is`(it)) rule.project else null
      }
      .let { event ->
        action.update(event)
        assertEquals("Show Issue Detail", event.presentation.text)
        assertTrue(event.presentation.isVisible)
        assertFalse(event.presentation.isEnabled)
      }

    service.setSharedIssuePanelVisibility(true)
    TestActionEvent.createTestEvent {
        when {
          PlatformDataKeys.PROJECT.`is`(it) -> rule.project
          PlatformDataKeys.SELECTED_ITEM.`is`(it) -> TestNode()
          else -> null
        }
      }
      .let { event ->
        action.update(event)
        assertEquals("Show Issue Detail", event.presentation.text)
        assertTrue(event.presentation.isVisible)
        assertFalse(event.presentation.isEnabled)
      }

    TestActionEvent.createTestEvent {
        when {
          PlatformDataKeys.PROJECT.`is`(it) -> rule.project
          PlatformDataKeys.SELECTED_ITEM.`is`(it) -> TestIssueNode(TestIssue())
          else -> null
        }
      }
      .let { event ->
        action.update(event)
        assertEquals("Show Issue Detail", event.presentation.text)
        assertTrue(event.presentation.isVisible)
        assertTrue(event.presentation.isEnabled)
      }
  }

  @Test
  fun testIsSelected() {
    val action = ToggleIssueDetailAction()
    val event =
      TestActionEvent.createTestEvent {
        when (it) {
          PlatformDataKeys.PROJECT.name -> rule.project
          // Ensure that an element is "selected" to enable the action
          PlatformDataKeys.SELECTED_ITEM.name -> TestIssueNode(TestIssue())
          else -> null
        }
      }

    service.setSharedIssuePanelVisibility(false)
    assertFalse(action.isSelected(event))

    service.setSharedIssuePanelVisibility(true)
    // Still not selected because of a the side panel not visible
    assertTrue(action.isSelected(event))
  }

  @Test
  fun testSelect() {
    val action = ToggleIssueDetailAction()
    val event =
      TestActionEvent.createTestEvent {
        if (PlatformDataKeys.PROJECT.`is`(it)) rule.project else null
      }
    service.setSharedIssuePanelVisibility(true)

    action.setSelected(event, false)
    assertFalse(service.getSelectedIssuePanel()!!.sidePanelVisible)

    action.setSelected(event, true)
    assertTrue(service.getSelectedIssuePanel()!!.sidePanelVisible)
  }
}

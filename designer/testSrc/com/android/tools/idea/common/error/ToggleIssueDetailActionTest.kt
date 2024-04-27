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

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.TestToolWindowManager
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndGet
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
    HeadlessDataManager.fallbackToProductionDataManager(rule.testRootDisposable)
    val manager = ToolWindowManager.getInstance(rule.project)
    toolWindow = manager.registerToolWindow(RegisterToolWindowTask(ProblemsView.ID))

    invokeAndWaitIfNeeded {
      service = IssuePanelService.getInstance(rule.project)
      service.initIssueTabs(toolWindow)
    }
  }

  @Test
  fun testUpdate() {
    val action = ToggleIssueDetailAction()
    val dataContext = runInEdtAndGet {
      DataManager.getInstance().getDataContext(toolWindow.contentManager.selectedContent?.component)
    }

    service.getSharedIssuePanel()!!.sidePanelVisible = false
    TestActionEvent.createTestEvent(dataContext).let { event ->
      action.update(event)
      assertEquals("Show Issue Detail", event.presentation.text)
      assertTrue(event.presentation.isVisible)
      assertFalse(event.presentation.isEnabled)
    }

    service.getSharedIssuePanel()!!.sidePanelVisible = true
    TestActionEvent.createTestEvent {
        when {
          PlatformDataKeys.SELECTED_ITEM.`is`(it) -> TestNode()
          else -> dataContext.getData(it)
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
          PlatformDataKeys.SELECTED_ITEM.`is`(it) -> TestIssueNode(TestIssue())
          else -> dataContext.getData(it)
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
    val dataContext = runInEdtAndGet {
      DataManager.getInstance().getDataContext(toolWindow.contentManager.selectedContent?.component)
    }
    val event =
      TestActionEvent.createTestEvent {
        when (it) {
          // Ensure that an element is "selected" to enable the action
          PlatformDataKeys.SELECTED_ITEM.name -> TestIssueNode(TestIssue())
          else -> dataContext.getData(it)
        }
      }

    service.getSharedIssuePanel()!!.sidePanelVisible = false
    assertFalse(action.isSelected(event))

    service.getSharedIssuePanel()!!.sidePanelVisible = true
    assertTrue(action.isSelected(event))
  }

  @Test
  fun testSelect() {
    val action = ToggleIssueDetailAction()
    val dataContext = runInEdtAndGet {
      DataManager.getInstance().getDataContext(toolWindow.contentManager.selectedContent?.component)
    }
    val event = TestActionEvent.createTestEvent(dataContext)

    action.setSelected(event, false)
    assertFalse(service.getSharedIssuePanel()!!.sidePanelVisible)

    action.setSelected(event, true)
    assertTrue(service.getSharedIssuePanel()!!.sidePanelVisible)
  }
}

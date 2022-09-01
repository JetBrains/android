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
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintSettings
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.assertInstanceOf
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class IssuePanelViewOptionActionGroupTest {

  @JvmField
  @Rule
  val rule = AndroidProjectRule.inMemory().onEdt()

  lateinit var context: DataContext

  @Before
  fun setup() {
    context = DataContext { dataId ->
      if (PlatformDataKeys.PROJECT.`is`(dataId)) {
        rule.project
      }
      else null
    }
  }

  @Test
  fun testOptions() {
    val group = IssuePanelViewOptionActionGroup()
    val actionEvent = TestActionEvent(context, group)

    val options = group.getChildren(actionEvent)

    val showWarningAction = options[0] as SeverityFilterAction
    val showWeakWarningAction = options[1] as SeverityFilterAction
    val showServerProblemAction = options[2] as SeverityFilterAction
    val showTypoAction = options[3] as SeverityFilterAction
    val showConsideration = options[4] as SeverityFilterAction
    val showVisualProblemAction = options[5] as VisualLintFilterAction
    assertInstanceOf<Separator>(options[6])
    val sortedBySeverityAction = options[7] as ToggleIssuePanelSortedBySeverityAction
    val sortedByNameAction = options[8] as ToggleIssuePanelSortedByNameAction

    val severityIterator = SeverityRegistrar.getSeverityRegistrar(rule.project).allSeverities.reversed()
      .filter { it != HighlightSeverity.INFO && it > HighlightSeverity.INFORMATION && it < HighlightSeverity.ERROR }
      .iterator()

    showWarningAction.let {
      assertEquals(severityIterator.next().myVal, it.severity)
      assertEquals("Show Warning", it.templateText)
    }

    showWeakWarningAction.let {
      assertEquals(severityIterator.next().myVal, it.severity)
      assertEquals("Show Weak Warning", it.templateText)
    }

    showServerProblemAction.let {
      assertEquals(severityIterator.next().myVal, it.severity)
      assertEquals("Show Server Problem", it.templateText)
    }

    showTypoAction.let {
      assertEquals(severityIterator.next().myVal, it.severity)
      assertEquals("Show Typo", it.templateText)
    }

    showConsideration.let {
      assertEquals(severityIterator.next().myVal, it.severity)
      assertEquals("Show Consideration", it.templateText)
    }

    assertFalse(severityIterator.hasNext())

    showVisualProblemAction.let {
      assertEquals("Show Screen Size Problem", it.templateText)
    }

    sortedBySeverityAction.let {
      assertEquals("Sort By Severity", it.templateText)
    }

    sortedByNameAction.let {
      assertEquals("Sort By Name", it.templateText)
    }
  }
}

class SeverityFilterActionTest {

  @JvmField
  @Rule
  val rule = AndroidProjectRule.inMemory().onEdt()

  @Test
  fun testSelected() {
    ProblemsViewState.getInstance(rule.project).hideBySeverity.clear()
    val severity = 10

    val action = SeverityFilterAction(rule.project, "", severity)
    assertTrue(action.isSelected(TestActionEvent()))

    ProblemsViewState.getInstance(rule.project).hideBySeverity.add(severity)
    assertFalse(action.isSelected(TestActionEvent()))

    ProblemsViewState.getInstance(rule.project).hideBySeverity.remove(severity)
    assertTrue(action.isSelected(TestActionEvent()))
  }

  @Test
  fun testPerform() {
    ProblemsViewState.getInstance(rule.project).hideBySeverity.clear()
    val severity = 10

    val action = SeverityFilterAction(rule.project, "", severity)

    action.setSelected(TestActionEvent(), true)
    assertFalse(ProblemsViewState.getInstance(rule.project).hideBySeverity.contains(severity))

    action.setSelected(TestActionEvent(), false)
    assertTrue(ProblemsViewState.getInstance(rule.project).hideBySeverity.contains(severity))

    action.setSelected(TestActionEvent(), true)
    assertFalse(ProblemsViewState.getInstance(rule.project).hideBySeverity.contains(severity))
  }
}

class VisualLintFilterActionTest {

  @JvmField
  @Rule
  val rule = AndroidProjectRule.inMemory().onEdt()

  @Test
  fun testSelected() {
    val action = VisualLintFilterAction(rule.project)
    VisualLintSettings.getInstance(rule.project).isVisualLintFilterSelected = true
    assertTrue(action.isSelected(TestActionEvent()))
    VisualLintSettings.getInstance(rule.project).isVisualLintFilterSelected = false
    assertFalse(action.isSelected(TestActionEvent()))
  }

  @Test
  fun testPerform() {
    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(rule.project)
    val service = IssuePanelService.getInstance(rule.project)
    service.initIssueTabs(toolWindow)
    toolWindow.contentManager.let { it.setSelectedContent(it.contents[0]) }
    val panel = service.getSelectedSharedIssuePanel()!!
    val visualLintIssue = mock<VisualLintRenderIssue>()

    VisualLintSettings.getInstance(rule.project).isVisualLintFilterSelected = true
    val action = VisualLintFilterAction(rule.project)
    assertTrue(panel.issueProvider.viewOptionFilter(visualLintIssue))
    assertTrue(panel.issueProvider.viewOptionFilter(visualLintIssue))

    action.setSelected(TestActionEvent(), false)
    assertFalse(VisualLintSettings.getInstance(rule.project).isVisualLintFilterSelected)
    assertFalse(panel.issueProvider.viewOptionFilter(visualLintIssue))

    action.setSelected(TestActionEvent(), true)
    assertTrue(VisualLintSettings.getInstance(rule.project).isVisualLintFilterSelected)
    assertTrue(panel.issueProvider.viewOptionFilter(visualLintIssue))
  }
}

class ToggleIssuePanelSortedBySeverityActionTest {

  @JvmField
  @Rule
  val rule = AndroidProjectRule.inMemory().onEdt()

  @Test
  fun testSelected() {
    val state = ProblemsViewState.getInstance(rule.project)
    val action = ToggleIssuePanelSortedBySeverityAction()
    val context = DataContext { key -> if (PlatformDataKeys.PROJECT.`is`(key)) rule.project else null }

    state.sortBySeverity = true
    assertTrue(action.isSelected(TestActionEvent(context)))

    state.sortBySeverity = false
    assertFalse(action.isSelected(TestActionEvent(context)))

    state.sortBySeverity = true
    assertTrue(action.isSelected(TestActionEvent(context)))
  }

  @Test
  fun testPerform() {
    val state = ProblemsViewState.getInstance(rule.project)
    val action = ToggleIssuePanelSortedBySeverityAction()
    val context = DataContext { key -> if (PlatformDataKeys.PROJECT.`is`(key)) rule.project else null }

    action.setSelected(TestActionEvent(context), true)
    assertTrue(state.sortBySeverity)

    action.setSelected(TestActionEvent(context), false)
    assertFalse(state.sortBySeverity)

    action.setSelected(TestActionEvent(context), true)
    assertTrue(state.sortBySeverity)
  }
}

class ToggleIssuePanelSortedByNameActionTest {

  @JvmField
  @Rule
  val rule = AndroidProjectRule.inMemory().onEdt()

  @Test
  fun testSelected() {
    val state = ProblemsViewState.getInstance(rule.project)
    val action = ToggleIssuePanelSortedByNameAction()
    val context = DataContext { key -> if (PlatformDataKeys.PROJECT.`is`(key)) rule.project else null }

    state.sortByName = true
    assertTrue(action.isSelected(TestActionEvent(context)))

    state.sortByName = false
    assertFalse(action.isSelected(TestActionEvent(context)))

    state.sortByName = true
    assertTrue(action.isSelected(TestActionEvent(context)))
  }

  @Test
  fun testPerform() {
    val state = ProblemsViewState.getInstance(rule.project)
    val action = ToggleIssuePanelSortedByNameAction()
    val context = DataContext { key -> if (PlatformDataKeys.PROJECT.`is`(key)) rule.project else null }

    action.setSelected(TestActionEvent(context), true)
    assertTrue(state.sortByName)

    action.setSelected(TestActionEvent(context), false)
    assertFalse(state.sortByName)

    action.setSelected(TestActionEvent(context), true)
    assertTrue(state.sortByName)
  }
}

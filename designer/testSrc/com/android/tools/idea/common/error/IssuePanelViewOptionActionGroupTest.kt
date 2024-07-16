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
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintSettings
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.TestActionEvent.createTestEvent
import com.intellij.testFramework.assertInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class IssuePanelViewOptionActionGroupTest {

  @JvmField @Rule val rule = AndroidProjectRule.inMemory().onEdt()

  lateinit var context: DataContext

  @Before
  fun setup() {
    context = DataContext { dataId ->
      if (PlatformDataKeys.PROJECT.`is`(dataId)) {
        rule.project
      } else null
    }
  }

  @Test
  fun testOptions() {
    val group = IssuePanelViewOptionActionGroup()
    val actionEvent = createTestEvent(group, context)

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

    val severityIterator =
      SeverityRegistrar.getSeverityRegistrar(rule.project)
        .allSeverities
        .reversed()
        .filter {
          it != HighlightSeverity.INFO &&
            it > HighlightSeverity.INFORMATION &&
            it < HighlightSeverity.ERROR
        }
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

    showVisualProblemAction.let { assertEquals("Show Screen Size Problem", it.templateText) }

    sortedBySeverityAction.let { assertEquals("Sort By Severity", it.templateText) }

    sortedByNameAction.let { assertEquals("Sort By Name", it.templateText) }
  }
}

class SeverityFilterActionTest {

  @JvmField @Rule val rule = AndroidProjectRule.inMemory().onEdt()

  @Before
  fun setUp() {
    rule.projectRule.replaceProjectService(
      DesignerCommonIssuePanelModelProvider::class.java,
      TestIssuePanelModelProvider(),
    )
  }

  @Test
  fun testSelected() {
    ProblemsViewState.getInstance(rule.project).hideBySeverity.clear()
    val severity = 10

    val action = SeverityFilterAction("", severity)
    assertTrue(action.isSelected(createTestEvent()))

    ProblemsViewState.getInstance(rule.project).hideBySeverity.add(severity)
    assertFalse(action.isSelected(createTestEvent()))

    ProblemsViewState.getInstance(rule.project).hideBySeverity.remove(severity)
    assertTrue(action.isSelected(createTestEvent()))
  }

  @Test
  fun testPerform() {
    ProblemsViewState.getInstance(rule.project).hideBySeverity.clear()
    val severity = 10

    val action = SeverityFilterAction("", severity)

    action.setSelected(createTestEvent(), true)
    assertFalse(ProblemsViewState.getInstance(rule.project).hideBySeverity.contains(severity))

    action.setSelected(createTestEvent(), false)
    assertTrue(ProblemsViewState.getInstance(rule.project).hideBySeverity.contains(severity))

    action.setSelected(createTestEvent(), true)
    assertFalse(ProblemsViewState.getInstance(rule.project).hideBySeverity.contains(severity))
  }
}

class VisualLintFilterActionTest {

  @JvmField @Rule val rule = AndroidProjectRule.inMemory().onEdt()

  @Test
  fun testSelected() {
    val action = VisualLintFilterAction()
    VisualLintSettings.getInstance(rule.project).isVisualLintFilterSelected = true
    assertTrue(action.isSelected(createTestEvent()))
    VisualLintSettings.getInstance(rule.project).isVisualLintFilterSelected = false
    assertFalse(action.isSelected(createTestEvent()))
  }

  @Test
  fun testPerform() {
    ToolWindowManager.getInstance(rule.project)
      .registerToolWindow(RegisterToolWindowTask(ProblemsView.ID))
    runBlocking(workerThread) {
      ProblemsViewToolWindowUtils.addTab(rule.project, SharedIssuePanelProvider(rule.project))
    }
    val panel = IssuePanelService.getDesignerCommonIssuePanel(rule.project)!!
    val visualLintIssue = mock<VisualLintRenderIssue>()
    val dataContext = DataContext {
      when (it) {
        DESIGNER_COMMON_ISSUE_PANEL.name -> panel
        CommonDataKeys.PROJECT.name -> rule.project
        else -> null
      }
    }

    VisualLintSettings.getInstance(rule.project).isVisualLintFilterSelected = true
    val action = VisualLintFilterAction()
    assertTrue(panel.issueProvider.viewOptionFilter(visualLintIssue))

    action.setSelected(createTestEvent(dataContext), false)
    assertFalse(VisualLintSettings.getInstance(rule.project).isVisualLintFilterSelected)
    assertFalse(panel.issueProvider.viewOptionFilter(visualLintIssue))

    action.setSelected(createTestEvent(dataContext), true)
    assertTrue(VisualLintSettings.getInstance(rule.project).isVisualLintFilterSelected)
    assertTrue(panel.issueProvider.viewOptionFilter(visualLintIssue))
  }
}

class ToggleIssuePanelSortedBySeverityActionTest {

  @JvmField @Rule val rule = AndroidProjectRule.inMemory().onEdt()

  @Test
  fun testSelected() {
    val state = ProblemsViewState.getInstance(rule.project)
    val action = ToggleIssuePanelSortedBySeverityAction()
    val context = DataContext { key ->
      if (PlatformDataKeys.PROJECT.`is`(key)) rule.project else null
    }

    state.sortBySeverity = true
    assertTrue(action.isSelected(createTestEvent(context)))

    state.sortBySeverity = false
    assertFalse(action.isSelected(createTestEvent(context)))

    state.sortBySeverity = true
    assertTrue(action.isSelected(createTestEvent(context)))
  }

  @Test
  fun testPerform() {
    val state = ProblemsViewState.getInstance(rule.project)
    val action = ToggleIssuePanelSortedBySeverityAction()
    val context = DataContext { key ->
      if (PlatformDataKeys.PROJECT.`is`(key)) rule.project else null
    }

    action.setSelected(createTestEvent(context), true)
    assertTrue(state.sortBySeverity)

    action.setSelected(createTestEvent(context), false)
    assertFalse(state.sortBySeverity)

    action.setSelected(createTestEvent(context), true)
    assertTrue(state.sortBySeverity)
  }
}

class ToggleIssuePanelSortedByNameActionTest {

  @JvmField @Rule val rule = AndroidProjectRule.inMemory().onEdt()

  @Test
  fun testSelected() {
    val state = ProblemsViewState.getInstance(rule.project)
    val action = ToggleIssuePanelSortedByNameAction()
    val context = DataContext { key ->
      if (PlatformDataKeys.PROJECT.`is`(key)) rule.project else null
    }

    state.sortByName = true
    assertTrue(action.isSelected(createTestEvent(context)))

    state.sortByName = false
    assertFalse(action.isSelected(createTestEvent(context)))

    state.sortByName = true
    assertTrue(action.isSelected(createTestEvent(context)))
  }

  @Test
  fun testPerform() {
    val state = ProblemsViewState.getInstance(rule.project)
    val action = ToggleIssuePanelSortedByNameAction()
    val context = DataContext { key ->
      if (PlatformDataKeys.PROJECT.`is`(key)) rule.project else null
    }

    action.setSelected(createTestEvent(context), true)
    assertTrue(state.sortByName)

    action.setSelected(createTestEvent(context), false)
    assertFalse(state.sortByName)

    action.setSelected(createTestEvent(context), true)
    assertTrue(state.sortByName)
  }
}

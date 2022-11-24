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
import com.android.tools.idea.testing.onEdt
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.assertInstanceOf
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
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
    val actionEvent = TestActionEvent.createTestEvent(group, context)

    val options = group.getChildren(actionEvent)

    val showWarningAction = options[0] as SeverityFilterAction
    val showWeakWarningAction = options[1] as SeverityFilterAction
    val showServerProblemAction = options[2] as SeverityFilterAction
    val showTypoAction = options[3] as SeverityFilterAction
    val showVisualProblemAction = options[4] as VisualLintFilterAction
    assertInstanceOf<Separator>(options[5])
    val sortedBySeverityAction = options[6] as ToggleIssuePanelSortedBySeverityAction
    val sortedByNameAction = options[7] as ToggleIssuePanelSortedByNameAction

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

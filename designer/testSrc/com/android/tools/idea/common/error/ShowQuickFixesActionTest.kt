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

import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEM
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import java.awt.Dimension
import java.awt.event.MouseEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class ShowQuickFixesActionTest {

  @JvmField @Rule val projectRule = AndroidProjectRule.inMemory()

  @JvmField @Rule val popupRule = JBPopupRule()

  @Test
  fun testUpdate() {
    val action = ShowQuickFixesAction()

    // Test no issue
    val emptyContextEvent = TestActionEvent.createTestEvent(action, DataContext.EMPTY_CONTEXT)
    action.update(emptyContextEvent)
    assertEquals(
      ActionsBundle.actionText("ProblemsView.QuickFixes") ?: "Show Quick Fix",
      emptyContextEvent.presentation.text,
    )
    assertFalse(emptyContextEvent.presentation.isEnabled)

    // Test issue without the fix
    val eventWithoutFix =
      TestActionEvent.createTestEvent(action) { IssueNode(null, TestIssue(), null) }
    action.update(eventWithoutFix)
    assertEquals("No Quick Fix for This Issue", eventWithoutFix.presentation.text)
    assertFalse(eventWithoutFix.presentation.isEnabled)

    // Test issue with a fix
    val eventWithFix =
      TestActionEvent.createTestEvent(action) {
        IssueNode(null, TestIssue(fixList = listOf(mock())), null)
      }
    action.update(eventWithFix)
    assertEquals(
      ActionsBundle.actionText("ProblemsView.QuickFixes") ?: "Show Quick Fix",
      eventWithFix.presentation.text,
    )
    assertTrue(eventWithFix.presentation.isEnabled)
  }

  @Test
  fun testCreatePopupAfterPerform() {
    val action = ShowQuickFixesAction()

    val sourceButton = ActionButton(action, Presentation(), "", Dimension(1, 1))
    val inputEvent = MouseEvent(sourceButton, 0, 0, 0, 0, 0, 1, true, MouseEvent.BUTTON1)
    val dataContext =
      SimpleDataContext.getSimpleContext(
        SELECTED_ITEM,
        IssueNode(null, TestIssue(fixList = listOf(mock())), null),
      )
    val eventWithFix =
      AnActionEvent.createEvent(
        dataContext,
        action.templatePresentation.clone(),
        "",
        ActionUiKind.NONE,
        inputEvent,
      )
    assertEquals(0, popupRule.fakePopupFactory.getChildPopups(sourceButton).size)
    action.actionPerformed(eventWithFix)
    assertEquals(1, popupRule.fakePopupFactory.getChildPopups(sourceButton).size)
  }
}

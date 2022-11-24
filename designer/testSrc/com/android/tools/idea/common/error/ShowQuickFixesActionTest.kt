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
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShowQuickFixesActionTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testUpdate() {
    val action = ShowQuickFixesAction()

    // Test no issue
    val emptyContextEvent = TestActionEvent.createTestEvent(action, DataContext.EMPTY_CONTEXT)
    action.update(emptyContextEvent)
    assertEquals(ActionsBundle.actionText("ProblemsView.QuickFixes") ?: "Show Quick Fix", emptyContextEvent.presentation.text)
    assertFalse(emptyContextEvent.presentation.isEnabled)

    // Test issue without the fix
    val eventWithoutFix = TestActionEvent.createTestEvent(action) { IssueNode(null, TestIssue(), null) }
    action.update(eventWithoutFix)
    assertEquals("No Quick Fix for This Issue", eventWithoutFix.presentation.text)
    assertFalse(eventWithoutFix.presentation.isEnabled)

    // Test issue with a fix
    val eventWithFix = TestActionEvent.createTestEvent(action) { IssueNode(null, TestIssue(fixList = listOf(mock())), null) }
    action.update(eventWithFix)
    assertEquals(ActionsBundle.actionText("ProblemsView.QuickFixes") ?: "Show Quick Fix", eventWithFix.presentation.text)
    assertTrue(eventWithFix.presentation.isEnabled)
  }
}

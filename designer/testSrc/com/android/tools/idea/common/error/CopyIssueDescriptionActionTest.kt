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
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndGet
import java.awt.datatransfer.DataFlavor
import javax.swing.JTree
import javax.swing.tree.TreePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock

class CopyIssueDescriptionActionTest {

  @JvmField @Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testUpdate() {
    val action = CopyIssueDescriptionAction()

    val issueNodeEvent = createIssueNodeEvent(action)
    action.update(issueNodeEvent)
    assertTrue(issueNodeEvent.presentation.isEnabledAndVisible)

    val emptyEvent = TestActionEvent.createTestEvent()
    action.update(emptyEvent)
    assertFalse(emptyEvent.presentation.isEnabledAndVisible)
  }

  @Test
  fun testPerform() {
    val action = CopyIssueDescriptionAction()
    val emptyEvent = TestActionEvent.createTestEvent()

    action.actionPerformed(emptyEvent)
    assertNull(CopyPasteManager.getInstance().contents)

    val issueNodeEvent1 = createIssueNodeEvent(action, "description")
    action.actionPerformed(issueNodeEvent1)
    assertEquals("description", CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor))

    val issueNodeEvent2 = createIssueNodeEvent(action, "another description")
    action.actionPerformed(issueNodeEvent2)
    assertEquals(
      "another description",
      CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor),
    )
  }

  private fun createIssueNodeEvent(action: AnAction, description: String = ""): AnActionEvent {
    val tree = mock<JTree>()
    val path = mock<TreePath>()
    // The summary is used when issue node shows in the tree, which is also used when copying.
    // The description of issue is used as the tooltips when hovering on the issue node.
    val node = IssueNode(null, TestIssue(summary = description), null)

    `when`(tree.selectionPath).thenReturn(path)
    `when`(path.lastPathComponent).thenReturn(node)

    val context = runInEdtAndGet {
      SimpleDataContext.getSimpleContext(PlatformCoreDataKeys.CONTEXT_COMPONENT, tree)
    }

    return TestActionEvent.createTestEvent(action, context)
  }
}

/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.preview.actions

import com.android.tools.idea.preview.groups.PreviewGroup
import com.android.tools.idea.preview.groups.PreviewGroupManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GroupSwitchActionTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testSetGroupActionEscapesXmlEntitiesInDisplayName() {
    val groupWithHtml = PreviewGroup.namedGroup("Group <b>with</b> \"HTML\" & 'More'")
    val action = GroupSwitchAction.SetGroupAction(groupWithHtml)

    // Verify the action name in the template presentation has been escaped and mnemonics disabled
    // StringUtil.escapeXmlEntities escapes "'" as "&#39;"
    assertEquals("Group &lt;b&gt;with&lt;/b&gt; &quot;HTML&quot; &amp; &#39;More&#39;", action.templatePresentation.text)
  }

  @Test
  fun testGroupSwitchActionEscapesXmlEntitiesInSelectedGroupDisplayName() {
    val groupWithHtml = PreviewGroup.namedGroup("Another <Group>")
    val previewGroupManager = mock<PreviewGroupManager>()
    whenever(previewGroupManager.availableGroupsFlow).thenReturn(MutableStateFlow(setOf(groupWithHtml)))
    whenever(previewGroupManager.groupFilter).thenReturn(groupWithHtml)

    val dataContext = SimpleDataContext.builder().add(PreviewGroupManager.KEY, previewGroupManager).build()

    val action = GroupSwitchAction()
    val testEvent = TestActionEvent.createTestEvent(dataContext)

    action.update(testEvent)
    assertTrue(testEvent.presentation.isVisible)
    assertEquals("Another &lt;Group&gt;", testEvent.presentation.text)
  }
}

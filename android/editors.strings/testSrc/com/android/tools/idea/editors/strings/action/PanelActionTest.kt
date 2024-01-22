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
package com.android.tools.idea.editors.strings.action

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.editors.strings.StringResourceEditor
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@RunWith(JUnit4::class)
class PanelActionTest {
  @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()
  @get:Rule val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()

  @Mock private lateinit var actionManager: ActionManager
  @Mock private lateinit var stringResourceEditor: StringResourceEditor

  private val falsePanelAction = TestPanelAction(false)
  private val truePanelAction = TestPanelAction(true)
  private val dataContextBuilder = SimpleDataContext.builder()
  private val project
    get() = projectRule.project
  // Lazy so that actionManager is initialized.
  private val e: AnActionEvent by lazy {
    AnActionEvent(null, dataContextBuilder.build(), "place", Presentation(), actionManager, 0)
  }

  @Test
  fun update_nullRequiredData() {
    falsePanelAction.update(e)

    assertThat(e.presentation.isEnabled).isFalse()
    // Should not invoke doUpdate because the required data are not there.
    assertThat(falsePanelAction.doUpdateInvoked).isFalse()

    truePanelAction.update(e)

    assertThat(e.presentation.isEnabled).isFalse()
    // Should not invoke doUpdate because the required data are not there.
    assertThat(truePanelAction.doUpdateInvoked).isFalse()
  }

  @Test
  fun update_nullFileEditor() {
    dataContextBuilder.add(CommonDataKeys.PROJECT, project)
    falsePanelAction.update(e)

    assertThat(e.presentation.isEnabled).isFalse()
    // Should not invoke doUpdate because the required data are not there.
    assertThat(falsePanelAction.doUpdateInvoked).isFalse()

    truePanelAction.update(e)

    assertThat(e.presentation.isEnabled).isFalse()
    // Should not invoke doUpdate because the required data are not there.
    assertThat(truePanelAction.doUpdateInvoked).isFalse()
  }

  @Test
  fun update_nullProject() {
    dataContextBuilder.add(PlatformDataKeys.FILE_EDITOR, stringResourceEditor)

    falsePanelAction.update(e)

    assertThat(e.presentation.isEnabled).isFalse()
    // Should not invoke doUpdate because the required data are not there.
    assertThat(falsePanelAction.doUpdateInvoked).isFalse()

    truePanelAction.update(e)

    assertThat(e.presentation.isEnabled).isFalse()
    // Should not invoke doUpdate because the required data are not there.
    assertThat(truePanelAction.doUpdateInvoked).isFalse()
  }

  @Test
  fun update_hasRequiredData() {
    dataContextBuilder
      .add(CommonDataKeys.PROJECT, project)
      .add(PlatformDataKeys.FILE_EDITOR, stringResourceEditor)

    falsePanelAction.update(e)

    assertThat(e.presentation.isEnabled).isFalse()
    // Should invoke doUpdate because the required data are there.
    assertThat(falsePanelAction.doUpdateInvoked).isTrue()

    truePanelAction.update(e)

    assertThat(e.presentation.isEnabled).isTrue()
    // Should invoke doUpdate because the required data are there.
    assertThat(truePanelAction.doUpdateInvoked).isTrue()
  }

  @Test
  fun getPanel() {
    dataContextBuilder
      .add(CommonDataKeys.PROJECT, project)
      .add(PlatformDataKeys.FILE_EDITOR, stringResourceEditor)

    whenever(stringResourceEditor.panel).thenReturn(mock())
    truePanelAction.actionPerformed(e)
    falsePanelAction.actionPerformed(e)
  }

  /** Concrete test class to test functionality in abstract base [PanelAction] class. */
  private inner class TestPanelAction(private val doUpdateResult: Boolean) : PanelAction() {
    var doUpdateInvoked = false
      private set

    override fun doUpdate(event: AnActionEvent): Boolean =
      doUpdateResult.also { doUpdateInvoked = true }

    override fun actionPerformed(event: AnActionEvent) {
      assertThat(event.panel).isSameAs(stringResourceEditor.panel)
    }
  }
}

/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.actions.RefreshRenderAction
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import java.awt.event.KeyEvent
import javax.swing.JTextField
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.AdditionalAnswers.delegatesTo
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class RefreshRenderActionTest {
  @get:Rule val projectRule = ProjectRule()

  @Test
  fun `action enabled by default for non NlDesignSurface`() {
    val renderAction = RefreshRenderAction.getInstance()
    val surface = mock<DesignSurface<*>>()
    val event =
      TestActionEvent.createTestEvent(
        renderAction,
        SimpleDataContext.getSimpleContext(
          DESIGN_SURFACE,
          surface,
        ),
      )
    renderAction.update(event)

    assertTrue(event.presentation.isEnabled)
    assertTrue(event.presentation.isVisible)
  }

  @Test
  fun `action disabled for events from a JTextField`() {
    val renderAction = RefreshRenderAction.getInstance()
    val surface = mock<DesignSurface<*>>()
    val textField = JTextField()

    // Replace the IdeFocusManager to make the action think that
    // the current focus is a text field.
    val customFocusManager = mock(
      IdeFocusManager::class.java,
      delegatesTo<Any?>(IdeFocusManager.getGlobalInstance()),
    )
    doReturn(textField).whenever(customFocusManager).focusOwner
    ApplicationManager.getApplication()
      .replaceService(
        IdeFocusManager::class.java,
        customFocusManager,
        projectRule.disposable
      )
    val event =
      TestActionEvent.createFromInputEvent(
        KeyEvent(textField, 0, 0, 0, 0, '0', 0),
        "",
        null,
        SimpleDataContext.getSimpleContext(
          DESIGN_SURFACE,
          surface,
        ),
      )
    renderAction.update(event)

    assertFalse(event.presentation.isEnabled)
    assertTrue(event.presentation.isVisible)
  }

  @Test
  fun `action is not supported shows disabled`() {
    val renderAction = RefreshRenderAction.getInstance()
    val surface = mock<NlDesignSurface>()
    val event =
      TestActionEvent.createTestEvent(
        renderAction,
        SimpleDataContext.getSimpleContext(
          DESIGN_SURFACE,
          surface,
        ),
      )
    renderAction.update(event)

    assertFalse(event.presentation.isEnabled)
    // By default, the action is still visible
    assertTrue(event.presentation.isVisible)
  }
}

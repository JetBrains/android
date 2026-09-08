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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class StopUiCheckPreviewActionTest {
  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun testUpdate() {
    val previewManager = TestComposePreviewManager()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.builder().add(COMPOSE_PREVIEW_MANAGER, previewManager).build())
    val action = StopUiCheckPreviewAction()

    // Default mode: action should not be visible
    previewManager.setMode(PreviewMode.Default())
    action.update(event)
    assertFalse(event.presentation.isVisible)
    assertTrue(event.presentation.isEnabled)
    assertEquals(true, event.presentation.getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR))

    // UiCheck mode: action should be visible and enabled
    previewManager.setMode(mock<PreviewMode.UiCheck>())
    action.update(event)
    assertTrue(event.presentation.isVisible)
    assertTrue(event.presentation.isEnabled)

    // UiCheck mode and refreshing: action should be visible but disabled
    previewManager.currentStatus = previewManager.currentStatus.copy(isRefreshing = true)
    action.update(event)
    assertTrue(event.presentation.isVisible)
    assertFalse(event.presentation.isEnabled)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun testActionPerformed() = runTest {
    val composePreviewManager: ComposePreviewManager = mock()
    val dataContext =
      SimpleDataContext.builder()
        .add(COMPOSE_PREVIEW_MANAGER, composePreviewManager)
        .add(PreviewModeManager.KEY, composePreviewManager)
        .build()
    val action = StopUiCheckPreviewAction()
    val event =
      TestActionEvent.createTestEvent(action, dataContext).apply {
        installCoroutineScope(this@runTest)
      }

    action.actionPerformed(event)
    advanceUntilIdle()

    verify(composePreviewManager).restorePrevious()
  }
}

/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions

import com.android.tools.idea.actions.LAYOUT_PREVIEW_HANDLER_KEY
import com.android.tools.idea.actions.LayoutPreviewHandler
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.testFramework.MapDataContext
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(JUnit4::class)
class DisableToolsVisibilityAndPositionInPreviewActionTest {
  private val disableToolsAction = DisableToolsVisibilityAndPositionInPreviewAction
  private val actionManager: ActionManagerEx = mock(ActionManagerEx::class.java)
  private lateinit var context: MapDataContext

  private val previewHandler: LayoutPreviewHandler = object: LayoutPreviewHandler {
    override var previewWithToolsVisibilityAndPosition: Boolean = false
  }

  private fun createActionEvent(): AnActionEvent {
    return AnActionEvent(null, context, "DesignSurface", disableToolsAction.templatePresentation.clone(), actionManager, 0)
  }

  @Before
  fun setUp() {
    context = MapDataContext().apply { put(LAYOUT_PREVIEW_HANDLER_KEY, previewHandler) }
  }

  @Test
  fun isSelected() {
    previewHandler.previewWithToolsVisibilityAndPosition = true
    assertFalse { disableToolsAction.isSelected(createActionEvent()) }

    previewHandler.previewWithToolsVisibilityAndPosition = false
    assertTrue { disableToolsAction.isSelected(createActionEvent()) }
  }

  @Test
  fun toggle() {
    previewHandler.previewWithToolsVisibilityAndPosition = true

    disableToolsAction.actionPerformed(createActionEvent())
    assertFalse { previewHandler.previewWithToolsVisibilityAndPosition }

    disableToolsAction.actionPerformed(createActionEvent())
    assertTrue { previewHandler.previewWithToolsVisibilityAndPosition }
  }
}
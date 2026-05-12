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
import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LookaheadVisualizationActionTest {
  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun testLookaheadVisualizationAction() {
    val previewManager = TestComposePreviewManager()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.builder().add(COMPOSE_PREVIEW_MANAGER, previewManager).build())
    val action = LookaheadVisualizationAction()

    previewManager.isLookaheadAnimationVisualDebuggingEnabled = false
    assertFalse(action.isSelected(event))

    previewManager.isLookaheadAnimationVisualDebuggingEnabled = true
    assertTrue(action.isSelected(event))

    action.setSelected(event, false)
    assertFalse(previewManager.isLookaheadAnimationVisualDebuggingEnabled)

    action.setSelected(event, true)
    assertTrue(previewManager.isLookaheadAnimationVisualDebuggingEnabled)
  }

  @Test
  fun testLookaheadLabelsAction() {
    val previewManager = TestComposePreviewManager()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.builder().add(COMPOSE_PREVIEW_MANAGER, previewManager).build())
    val action = LookaheadLabelsAction()

    previewManager.isLookaheadAnimationVisualDebuggingKeyLabelEnabled = false
    assertFalse(action.isSelected(event))

    previewManager.isLookaheadAnimationVisualDebuggingKeyLabelEnabled = true
    assertTrue(action.isSelected(event))

    action.setSelected(event, false)
    previewManager.isLookaheadAnimationVisualDebuggingKeyLabelEnabled = false

    action.setSelected(event, true)
    previewManager.isLookaheadAnimationVisualDebuggingKeyLabelEnabled = true
  }

  @Test
  fun testLookaheadLabelsActionEnabledState() {
    val previewManager = TestComposePreviewManager()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.builder().add(COMPOSE_PREVIEW_MANAGER, previewManager).build())
    val action = LookaheadLabelsAction()

    previewManager.isLookaheadAnimationVisualDebuggingEnabled = false
    action.update(event)
    assertFalse(event.presentation.isEnabled)

    previewManager.isLookaheadAnimationVisualDebuggingEnabled = true
    action.update(event)
    assertTrue(event.presentation.isEnabled)
  }
}

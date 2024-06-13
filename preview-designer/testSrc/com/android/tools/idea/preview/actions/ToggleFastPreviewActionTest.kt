/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.TestActionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class ToggleFastPreviewActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `action toggles FastPreviewManager enabled state`() {
    val manager = FastPreviewManager.getInstance(projectRule.project)
    assertTrue(manager.isEnabled)
    assertTrue(manager.isAvailable)
    val action = ToggleFastPreviewAction(fastPreviewSurfaceProvider = { null })
    val event = TestActionEvent.createTestEvent()

    action.actionPerformed(event)
    assertFalse(manager.isEnabled)
    assertFalse(manager.isAvailable)

    action.update(event) // Manually trigger update, we can check the action presentation
    assertEquals("Enable Live Updates", event.presentation.text)

    action.actionPerformed(event)
    assertTrue(manager.isEnabled)
    assertTrue(manager.isAvailable)

    action.update(event) // Manually trigger update, we can check the action presentation
    assertEquals("Disable Live Updates", event.presentation.text)
  }
}

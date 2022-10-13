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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.FastPreviewRule
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.TestActionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

internal class ToggleFastPreviewActionTest {
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val chainRule: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(FastPreviewRule())

  @Test
  fun `is action visible when Fast Preview depending on the flag values`() {
    try {
      listOf(false, true).forEach {
        StudioFlags.COMPOSE_FAST_PREVIEW.override(it)

        val action = ToggleFastPreviewAction()
        val event = TestActionEvent()
        action.update(event)

        assertEquals(it, event.presentation.isVisible)
      }
    }
    finally {
      StudioFlags.COMPOSE_FAST_PREVIEW.clearOverride()
      LiveEditApplicationConfiguration.getInstance().resetDefault()
    }
  }

  @Test
  fun `action toggles FastPreviewManager enabled state`() {
    val manager = FastPreviewManager.getInstance(projectRule.project)
    assertTrue(manager.isEnabled)
    assertTrue(manager.isAvailable)
    val action = ToggleFastPreviewAction()
    val event = TestActionEvent()

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
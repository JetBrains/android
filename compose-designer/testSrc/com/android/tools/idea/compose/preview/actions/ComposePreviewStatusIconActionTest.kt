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

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.android.tools.idea.editors.fast.DisableReason
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.FastPreviewRule
import com.android.tools.idea.editors.fast.ManualDisabledReason
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.AnimatedIcon
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposePreviewStatusIconActionTest {
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val chain: TestRule = RuleChain.outerRule(projectRule)
    .around(FastPreviewRule())

  private val composePreviewManager = TestComposePreviewManager()

  // DataContext is lazy, so we give projectRule time to initialize itself.
  private val context by lazy {
    MapDataContext().also {
      it.put(COMPOSE_PREVIEW_MANAGER, composePreviewManager)
      it.put(CommonDataKeys.PROJECT, projectRule.project)
    }
  }

  private val originStatus = ComposePreviewManager.Status(
    hasRuntimeErrors = false,
    hasSyntaxErrors = false,
    isOutOfDate = false,
    isRefreshing = false,
    interactiveMode = ComposePreviewManager.InteractiveMode.DISABLED
  )

  @Test
  fun iconStatesTest() {
    val action = ComposePreviewStatusIconAction(null)
    val event = TestActionEvent(context)

    action.update(event)
    assertFalse(event.presentation.isEnabled)
    assertTrue(event.presentation.isVisible)
    assertEquals(AllIcons.General.InspectionsOK, event.presentation.disabledIcon)

    composePreviewManager.currentStatus = originStatus.copy(
      isOutOfDate = true
    )
    action.update(event)
    // When FastPreview is enabled, the preview is never out of date.
    assertFalse(event.presentation.isEnabled)
    assertTrue(event.presentation.isVisible)
    assertEquals(AllIcons.General.InspectionsOK, event.presentation.disabledIcon)

    try {
      // Not Icon shown when out of date
      FastPreviewManager.getInstance(projectRule.project).disable(ManualDisabledReason)
      action.update(event)
      assertFalse(event.presentation.isEnabled)
      assertFalse(event.presentation.isVisible)

      // Icon shown when fast preview manually disabled and up-to-date
      composePreviewManager.currentStatus = originStatus.copy()
      action.update(event)
      assertFalse(event.presentation.isEnabled)
      assertTrue(event.presentation.isVisible)
      assertEquals(AllIcons.General.InspectionsOK, event.presentation.disabledIcon)
    }
    finally {
      FastPreviewManager.getInstance(projectRule.project).enable()
    }

    try {
      // Icon not shown when auto disabled
      FastPreviewManager.getInstance(projectRule.project).disable(DisableReason("Auto-Disabled"))
      action.update(event)
      assertFalse(event.presentation.isEnabled)
      assertFalse(event.presentation.isVisible)
    }
    finally {
      FastPreviewManager.getInstance(projectRule.project).enable()
    }

    // Loading icon
    composePreviewManager.currentStatus = originStatus.copy(
      isRefreshing = true
    )
    action.update(event)
    assertFalse(event.presentation.isEnabled)
    assertTrue(event.presentation.isVisible)
    assertTrue(event.presentation.disabledIcon is AnimatedIcon.Default)
  }
}
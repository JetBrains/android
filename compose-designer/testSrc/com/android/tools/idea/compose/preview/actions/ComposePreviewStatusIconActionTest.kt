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
import icons.StudioIcons
import org.junit.After
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

  private val tf = listOf(true, false)
  private val fastPreviewDisableReasons = listOf(null, ManualDisabledReason, DisableReason("Auto-Disabled"))

  @After
  fun tearDown() {
    // Make sure to always re-enable fast preview
    FastPreviewManager.getInstance(projectRule.project).enable()
  }

  @Test
  fun testIconState_SyntaxError() {
    val action = ComposePreviewStatusIconAction(null)
    val event = TestActionEvent(context)

    // Syntax error has priority over the other properties
    for (runtimeError in tf) {
      for (outOfDate in tf) {
        for (refreshing in tf) {
          for (fastPreviewDisableReason in fastPreviewDisableReasons) {
            updateFastPreviewStatus(fastPreviewDisableReason)
            composePreviewManager.currentStatus = originStatus.copy(
              hasRuntimeErrors = runtimeError,
              hasSyntaxErrors = true,
              isOutOfDate = outOfDate,
              isRefreshing = refreshing)
            action.update(event)
            // When there is a syntax error, the icon is hidden
            assertFalse(event.presentation.isEnabled)
            assertFalse(event.presentation.isVisible)
          }
        }
      }
    }
  }

  @Test
  fun testIconState_FastPreview() {
    val action = ComposePreviewStatusIconAction(null)
    val event = TestActionEvent(context)

    // When no syntax error, FastPreview has priority over the other properties
    FastPreviewManager.getInstance(projectRule.project).enable()
    for (runtimeError in tf) {
      for (outOfDate in tf) {
        for (refreshing in tf) {
          composePreviewManager.currentStatus = originStatus.copy(
            hasRuntimeErrors = runtimeError,
            hasSyntaxErrors = false,
            isOutOfDate = outOfDate,
            isRefreshing = refreshing)
          action.update(event)
          // When no syntax error and FastPreview enabled, the icon is always visible
          assertTrue(event.presentation.isVisible)
          testIconPriorities(event, fastPreview = true, runtimeError, outOfDate, refreshing)
        }
      }
    }
  }

  @Test
  fun testIconState_FastPreviewAutoDisabled() {
    val action = ComposePreviewStatusIconAction(null)
    val event = TestActionEvent(context)

    FastPreviewManager.getInstance(projectRule.project).disable(DisableReason("Auto-Disabled"))
    for (runtimeError in tf) {
      for (outOfDate in tf) {
        for (refreshing in tf) {
          composePreviewManager.currentStatus = originStatus.copy(
            hasRuntimeErrors = runtimeError,
            hasSyntaxErrors = true,
            isOutOfDate = outOfDate,
            isRefreshing = refreshing)
          action.update(event)
          // When no syntax error, and FastPreview auto-disabled, the icon is never visible
          assertFalse(event.presentation.isEnabled)
          assertFalse(event.presentation.isVisible)
        }
      }
    }
  }

  @Test
  fun testIconState_FastPreviewManuallyDisabled() {
    val action = ComposePreviewStatusIconAction(null)
    val event = TestActionEvent(context)

    // When no syntax error and FastPreview manually disabled
    FastPreviewManager.getInstance(projectRule.project).disable(ManualDisabledReason)
    for (runtimeError in tf) {
      for (outOfDate in tf) {
        for (refreshing in tf) {
          composePreviewManager.currentStatus = originStatus.copy(
            hasRuntimeErrors = runtimeError,
            hasSyntaxErrors = false,
            isOutOfDate = outOfDate,
            isRefreshing = refreshing)
          action.update(event)
          testIconPriorities(event, fastPreview = false, runtimeError, outOfDate, refreshing)
        }
      }
    }
  }

  private fun testIconPriorities(event: TestActionEvent,
                                 fastPreview: Boolean,
                                 runtimeError: Boolean,
                                 outOfDate: Boolean,
                                 refreshing: Boolean) {
    // Out of date has priority over the rest, but only matters when FastPreview is disabled
    if (outOfDate && !fastPreview) {
      assertFalse(event.presentation.isEnabled)
      assertFalse(event.presentation.isVisible)
    }
    // Loading has priority over the other icons
    else if (refreshing) {
      assertTrue(event.presentation.isVisible)
      assertFalse(event.presentation.isEnabled)
      assertTrue(event.presentation.disabledIcon is AnimatedIcon.Default)
      assertEquals(null, event.presentation.text)
    }
    // Then render/runtime errors
    else if (runtimeError) {
      assertTrue(event.presentation.isVisible)
      assertTrue(event.presentation.isEnabled)
      assertEquals(StudioIcons.Common.WARNING, event.presentation.icon)
      assertTrue(event.presentation.text != null)
    }
    // When not refreshing and no render/runtime errors, then ok
    else {
      assertTrue(event.presentation.isVisible)
      assertFalse(event.presentation.isEnabled)
      assertEquals(AllIcons.General.InspectionsOK, event.presentation.disabledIcon)
      assertEquals(null, event.presentation.text)
    }
  }

  private fun updateFastPreviewStatus(disableReason: DisableReason?) {
    if (disableReason == null) {
      FastPreviewManager.getInstance(projectRule.project).enable()
    }
    else {
      FastPreviewManager.getInstance(projectRule.project).disable(disableReason)
    }
  }
}

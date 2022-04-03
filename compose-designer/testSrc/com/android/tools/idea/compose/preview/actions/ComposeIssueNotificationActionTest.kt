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
import com.android.tools.idea.compose.preview.fast.DisableReason
import com.android.tools.idea.compose.preview.fast.FastPreviewManager
import com.android.tools.idea.compose.preview.fast.FastPreviewRule
import com.android.tools.idea.compose.preview.fast.ManualDisabledReason
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule


internal class ComposeIssueNotificationActionTest {
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val chain: TestRule = RuleChain.outerRule(projectRule)
    .around(FastPreviewRule())

  private val composePreviewManager = TestComposePreviewManager()

  // DataContext is lazy so we give projectRule time to initialize itself.
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
  fun `check simple states`() {
    val action = ComposeIssueNotificationAction()
    val event = TestActionEvent(context)

    action.update(event)
    assertEquals("Up-to-date (The preview is up to date)", event.presentation.toString())

    composePreviewManager.currentStatus = originStatus.copy(
      hasRuntimeErrors = true
    )
    action.update(event)
    assertEquals("Up-to-date (The preview is up to date)", event.presentation.toString())

    composePreviewManager.currentStatus = originStatus.copy(
      isOutOfDate = true
    )
    action.update(event)
    // When FastPreview is enabled, the preview is never out of date.
    assertEquals("Up-to-date (The preview is up to date)", event.presentation.toString())
    try {
      FastPreviewManager.getInstance(projectRule.project).disable(ManualDisabledReason)
      action.update(event)
      assertEquals("Out of date (The preview is out of date)", event.presentation.toString())
    } finally {
      FastPreviewManager.getInstance(projectRule.project).enable()
    }

    composePreviewManager.currentStatus = originStatus.copy(
      hasSyntaxErrors = true
    )
    action.update(event)
    assertEquals("Syntax error (The preview will not update while your project contains syntax errors.)", event.presentation.toString())

    composePreviewManager.currentStatus = originStatus.copy(
      isRefreshing = true
    )
    action.update(event)
    assertEquals("Loading... (The preview is updating...)", event.presentation.toString())
  }

  @Test
  fun `check state priorities`() {
    val action = ComposeIssueNotificationAction()
    val event = TestActionEvent(context)

    composePreviewManager.currentStatus = originStatus.copy(
      hasSyntaxErrors = true,
      isOutOfDate = true
    )
    action.update(event)
    // Syntax errors take precedence over out of date
    assertEquals("Syntax error (The preview will not update while your project contains syntax errors.)", event.presentation.toString())

    composePreviewManager.currentStatus = originStatus.copy(
      hasSyntaxErrors = true,
      isOutOfDate = true,
      isRefreshing = true
    )
    action.update(event)
    assertEquals("Loading... (The preview is updating...)", event.presentation.toString())
  }
}
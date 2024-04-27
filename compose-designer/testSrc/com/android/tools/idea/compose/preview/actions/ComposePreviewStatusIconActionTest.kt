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

import com.android.ide.common.rendering.api.Result
import com.android.tools.idea.actions.SCENE_VIEW
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.android.tools.idea.editors.fast.DisableReason
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.ManualDisabledReason
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.rendering.RenderLogger
import com.android.tools.rendering.RenderResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import icons.StudioIcons
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class ComposePreviewStatusIconActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val composePreviewManager = TestComposePreviewManager()

  // DataContext is lazy, so we give projectRule time to initialize itself.
  private val context by lazy {
    MapDataContext().also {
      it.put(COMPOSE_PREVIEW_MANAGER, composePreviewManager)
      it.put(CommonDataKeys.PROJECT, projectRule.project)
      it.put(SCENE_VIEW, sceneViewMock)
    }
  }

  private val originStatus =
    ComposePreviewManager.Status(
      hasRuntimeErrors = false,
      hasSyntaxErrors = false,
      isOutOfDate = false,
      areResourcesOutOfDate = false,
      isRefreshing = false,
    )

  private val tf = listOf(true, false)
  private val fastPreviewDisableReasons =
    listOf(null, ManualDisabledReason, DisableReason("Auto-Disabled"))

  private val sceneViewMock = Mockito.mock(SceneView::class.java)
  private val sceneManagerMock = Mockito.mock(LayoutlibSceneManager::class.java)
  private val renderResultMock = Mockito.mock(RenderResult::class.java)
  private val renderLoggerMock = Mockito.mock(RenderLogger::class.java)
  private val resultMock = Mockito.mock(Result::class.java)
  private var renderError = false

  init {
    Mockito.`when`(sceneViewMock.sceneManager).then {
      return@then sceneManagerMock
    }
    Mockito.`when`(sceneManagerMock.renderResult).then {
      return@then renderResultMock
    }
    Mockito.`when`(renderResultMock.logger).then {
      return@then renderLoggerMock
    }
    Mockito.`when`(renderLoggerMock.hasErrors()).then {
      return@then renderError
    }
    Mockito.`when`(resultMock.isSuccess).then {
      return@then !renderError
    }
    Mockito.`when`(renderResultMock.renderResult).then {
      return@then resultMock
    }
  }

  @After
  fun tearDown() {
    // Make sure to always re-enable fast preview
    FastPreviewManager.getInstance(projectRule.project).enable()
  }

  @Test
  fun testIconState() {
    val action = ComposePreviewStatusIconAction()
    val event = TestActionEvent.createTestEvent(context)

    // Syntax error has priority over the other properties
    for (syntaxError in tf) {
      for (runtimeError in tf) {
        for (renderError in tf) {
          for (outOfDate in tf) {
            for (refreshing in tf) {
              for (fastPreviewDisableReason in fastPreviewDisableReasons) {
                updateFastPreviewStatus(fastPreviewDisableReason)
                this.renderError = renderError
                val status =
                  originStatus.copy(
                    hasRuntimeErrors = runtimeError,
                    hasSyntaxErrors = syntaxError,
                    isOutOfDate = outOfDate,
                    isRefreshing = refreshing
                  )
                composePreviewManager.currentStatus = status
                action.update(event)
                val expectedToShowIcon = renderError && !refreshing
                assertEquals(
                  """
                    isEnabled has the wrong state (expected=$expectedToShowIcon, was=${event.presentation.isEnabled})
                    status=$status
                  """
                    .trimIndent(),
                  expectedToShowIcon,
                  event.presentation.isEnabled
                )
                assertEquals(
                  """
                    isVisible has the wrong state (expected=$expectedToShowIcon, was=${event.presentation.isEnabled})
                    status=$status
                  """
                    .trimIndent(),
                  expectedToShowIcon,
                  event.presentation.isVisible
                )
                if (expectedToShowIcon) {
                  assertEquals(StudioIcons.Common.WARNING, event.presentation.icon)
                }
              }
            }
          }
        }
      }
    }
  }

  private fun updateFastPreviewStatus(disableReason: DisableReason?) {
    if (disableReason == null) {
      FastPreviewManager.getInstance(projectRule.project).enable()
    } else {
      FastPreviewManager.getInstance(projectRule.project).disable(disableReason)
    }
  }
}

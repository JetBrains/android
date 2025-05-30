/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.ide.common.rendering.api.Result
import com.android.tools.idea.actions.SCENE_VIEW
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.rendering.RenderLogger
import com.android.tools.rendering.RenderResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestActionEvent
import icons.StudioIcons
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

private class TestStatus(
  override val isRefreshing: Boolean = false,
  override val hasRenderErrors: Boolean = false,
  override val hasSyntaxErrors: Boolean = false,
  override val isOutOfDate: Boolean = false,
  override val areResourcesOutOfDate: Boolean = false,
  override val previewedFile: PsiFile? = null,
) : PreviewViewModelStatus

class PreviewStatusIconTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private var currentStatus: PreviewViewModelStatus = TestStatus()

  private val context
    get() =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, projectRule.project)
        .add(SCENE_VIEW, sceneViewMock)
        .add(PREVIEW_VIEW_MODEL_STATUS, currentStatus)
        .build()

  private val tf = listOf(true, false)

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
    val action = PreviewStatusIcon()

    // Syntax error has priority over the other properties
    for (syntaxError in tf) {
      for (runtimeError in tf) {
        for (renderError in tf) {
          for (outOfDate in tf) {
            for (refreshing in tf) {
              for (enableState in tf) {
                updateFastPreviewStatus(enableState)
                this.renderError = renderError
                val status =
                  TestStatus(
                    hasRenderErrors = runtimeError,
                    hasSyntaxErrors = syntaxError,
                    isOutOfDate = outOfDate,
                    isRefreshing = refreshing,
                  )
                currentStatus = status
                val event = TestActionEvent.createTestEvent(context)
                action.update(event)
                val expectedToShowIcon = renderError && !refreshing
                assertEquals(
                  """
                    isEnabled has the wrong state (expected=$expectedToShowIcon, was=${event.presentation.isEnabled})
                    status=$status
                  """
                    .trimIndent(),
                  expectedToShowIcon,
                  event.presentation.isEnabled,
                )
                assertEquals(
                  """
                    isVisible has the wrong state (expected=$expectedToShowIcon, was=${event.presentation.isEnabled})
                    status=$status
                  """
                    .trimIndent(),
                  expectedToShowIcon,
                  event.presentation.isVisible,
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

  private fun updateFastPreviewStatus(enableFastPreview: Boolean) {
    if (enableFastPreview) {
      FastPreviewManager.getInstance(projectRule.project).enable()
    } else {
      FastPreviewManager.getInstance(projectRule.project).disable()
    }
  }
}

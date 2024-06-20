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

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.actions.prettyPrintActions
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.common.layout.option.SurfaceLayoutManager
import com.android.tools.idea.common.surface.layout.EmptySurfaceLayoutManager
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.ScreenViewProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestActionEvent
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CommonViewControlActionTest {

  @JvmField @Rule val rule = AndroidProjectRule.inMemory().onEdt()

  private val viewModelStatus =
    object : PreviewViewModelStatus {
      override var isRefreshing: Boolean = true
      override var hasErrorsAndNeedsBuild: Boolean = true
      override var hasSyntaxErrors: Boolean = true
      override var isOutOfDate: Boolean = true
      override val areResourcesOutOfDate: Boolean = true
      override var previewedFile: PsiFile? = null
    }

  private val dataContext = DataContext {
    when (it) {
      PREVIEW_VIEW_MODEL_STATUS.name -> viewModelStatus
      else -> null
    }
  }

  @Test
  fun testLayoutOptions() {
    val options =
      listOf(
        createOption("Layout A", EmptySurfaceLayoutManager()),
        createOption("Layout B", EmptySurfaceLayoutManager()),
        createOption("Layout C", EmptySurfaceLayoutManager()),
      )

    val viewControlAction = CommonViewControlAction(options)

    val expected =
      """View Control
    Switch Layout
    Layout A
    Layout B
    Layout C
"""

    val screenViewProviderMock = mock<ScreenViewProvider>()

    val designSurfaceMock = mock<NlDesignSurface>()
    whenever(designSurfaceMock.screenViewProvider).thenReturn(screenViewProviderMock)
    val dataContext = DataContext { if (DESIGN_SURFACE.`is`(it)) designSurfaceMock else null }

    val actionContent = prettyPrintActions(viewControlAction, dataContext = dataContext)
    assertEquals(expected, actionContent)
  }

  @Test
  fun testNotEnabledWhenRefreshing() {
    val event = TestActionEvent.createTestEvent(dataContext)
    val viewControlAction =
      CommonViewControlAction(listOf(createOption("Layout A", EmptySurfaceLayoutManager())))

    viewModelStatus.isRefreshing = false
    viewControlAction.update(event)
    assertTrue(event.presentation.isEnabled)

    viewModelStatus.isRefreshing = true
    viewControlAction.update(event)
    Assert.assertFalse(event.presentation.isEnabled)

    viewModelStatus.isRefreshing = false
    viewControlAction.update(event)
    assertTrue(event.presentation.isEnabled)
  }
}

private fun createOption(
  displayText: String,
  layoutManager: SurfaceLayoutManager,
): SurfaceLayoutOption {
  return SurfaceLayoutOption(displayText, layoutManager)
}

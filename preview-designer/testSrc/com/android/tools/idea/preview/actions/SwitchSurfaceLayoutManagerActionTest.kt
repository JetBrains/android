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

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.preview.TestPreviewElement
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.modes.GALLERY_LAYOUT_OPTION
import com.android.tools.idea.preview.modes.GRID_LAYOUT_OPTION
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.modes.UiCheckInstance
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.preview.PreviewElement
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SwitchSurfaceLayoutManagerActionTest {

  @JvmField @Rule val rule = AndroidProjectRule.inMemory()

  @Test
  fun testPreviewModeIsUpdatedWithGalleryModeOption() {
    val previewElement1 = TestPreviewElement("preview element 1")
    val previewElement2 = TestPreviewElement("preview element 2")
    val (dataContext, previewModeManager, _) =
      setupTestData(listOf(previewElement1, previewElement2))

    val actionWithGalleryModeOption =
      SwitchSurfaceLayoutManagerAction(listOf(GRID_LAYOUT_OPTION, GALLERY_LAYOUT_OPTION))

    val setGalleryOption =
      actionWithGalleryModeOption.SetSurfaceLayoutManagerAction(GALLERY_LAYOUT_OPTION)

    // check that no mode is set when the state is false
    run {
      setGalleryOption.setSelected(TestActionEvent.createTestEvent(dataContext), state = false)
      verifyNoInteractions(previewModeManager)
    }

    // check that the gallery mode is set with the first preview element
    run {
      setGalleryOption.setSelected(TestActionEvent.createTestEvent(dataContext), state = true)
      verify(previewModeManager, times(1)).setMode(PreviewMode.Gallery(previewElement1))
    }
  }

  @Test
  fun testPreviewModeIsUpdatedWithoutGalleryModeOption() {
    val (dataContext, previewModeManager, _) = setupTestData()
    val actionWithGalleryModeOption = SwitchSurfaceLayoutManagerAction(listOf(GRID_LAYOUT_OPTION))

    val setGridOption =
      actionWithGalleryModeOption.SetSurfaceLayoutManagerAction(GRID_LAYOUT_OPTION)

    // check that no mode is set when the state is false
    run {
      setGridOption.setSelected(TestActionEvent.createTestEvent(dataContext), state = false)
      verifyNoInteractions(previewModeManager)
    }

    // check that the gallery mode is set with the first preview element
    run {
      setGridOption.setSelected(TestActionEvent.createTestEvent(dataContext), state = true)
      verify(previewModeManager, times(1)).setMode(PreviewMode.Default(GRID_LAYOUT_OPTION))
    }
  }

  @Test
  fun testPreviewModeResetsToDefaultWhenCurrentModeIsGalleryMode() {
    val previewElement1 = TestPreviewElement("preview element 1")
    val previewElement2 = TestPreviewElement("preview element 2")
    val (dataContext, previewModeManager, _) =
      setupTestData(
        listOf(previewElement1, previewElement2),
        currentPreviewMode = PreviewMode.Gallery(previewElement2),
      )

    val actionWithGalleryModeOption =
      SwitchSurfaceLayoutManagerAction(listOf(GRID_LAYOUT_OPTION, GALLERY_LAYOUT_OPTION))

    val setGridLayoutOption =
      actionWithGalleryModeOption.SetSurfaceLayoutManagerAction(GRID_LAYOUT_OPTION)

    // check that no mode is set when the state is false
    run {
      setGridLayoutOption.setSelected(TestActionEvent.createTestEvent(dataContext), state = false)
      verifyNoInteractions(previewModeManager)
    }

    // check that the default mode is set with the grid layout option
    run {
      setGridLayoutOption.setSelected(TestActionEvent.createTestEvent(dataContext), state = true)
      verify(previewModeManager, times(1)).setMode(PreviewMode.Default(GRID_LAYOUT_OPTION))
    }
  }

  @Test
  fun testPreviewModeDerivesCurrentModeWithLayoutWithGalleryOption() {
    val previewElement1 = TestPreviewElement("preview element 1")
    val previewElement2 = TestPreviewElement("preview element 2")
    val uiCheckMode =
      PreviewMode.UiCheck(
        UiCheckInstance(previewElement2, isWearPreview = false),
        layoutOption = GRID_LAYOUT_OPTION,
      )
    val (dataContext, previewModeManager, _) =
      setupTestData(listOf(previewElement1, previewElement2), currentPreviewMode = uiCheckMode)

    val actionWithGalleryModeOption =
      SwitchSurfaceLayoutManagerAction(listOf(GRID_LAYOUT_OPTION, GALLERY_LAYOUT_OPTION))

    val setGridLayoutOption =
      actionWithGalleryModeOption.SetSurfaceLayoutManagerAction(GRID_LAYOUT_OPTION)

    // check that no mode is set when the state is false
    run {
      setGridLayoutOption.setSelected(TestActionEvent.createTestEvent(dataContext), state = false)
      verifyNoInteractions(previewModeManager)
    }

    // check that the current mode is derived with the selected layout option
    run {
      setGridLayoutOption.setSelected(TestActionEvent.createTestEvent(dataContext), state = true)
      val derivedOption = uiCheckMode.deriveWithLayout(GRID_LAYOUT_OPTION)
      verify(previewModeManager, times(1)).setMode(derivedOption)
    }
  }

  private data class TestData(
    val dataContext: DataContext,
    val previewModeManager: PreviewModeManager,
    val previewFlowManager: PreviewFlowManager<*>,
  )

  private fun setupTestData(
    previewElements: Collection<PreviewElement<*>> = emptyList(),
    currentPreviewMode: PreviewMode = PreviewMode.Default(),
  ): TestData {
    val previewFlowManager = mock<PreviewFlowManager<*>>()
    whenever(previewFlowManager.allPreviewElementsFlow)
      .thenReturn(MutableStateFlow(FlowableCollection.Present(previewElements)))

    val previewModeManager = mock<PreviewModeManager>()
    whenever(previewModeManager.mode).thenReturn(MutableStateFlow(currentPreviewMode))

    val designSurface = mock<NlDesignSurface>()

    val dataContext =
      SimpleDataContext.builder()
        .add(PreviewModeManager.KEY, previewModeManager)
        .add(PreviewFlowManager.KEY, previewFlowManager)
        .add(DESIGN_SURFACE, designSurface)
        .build()
    return TestData(dataContext, previewModeManager, previewFlowManager)
  }
}

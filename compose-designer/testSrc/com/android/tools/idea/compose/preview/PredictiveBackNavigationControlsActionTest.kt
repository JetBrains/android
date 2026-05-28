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
package com.android.tools.idea.compose.preview

import com.android.tools.adtui.ZoomController
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PredictiveBackNavigationControlsActionTest {

  @get:Rule val applicationRule = ApplicationRule()

  private val fakeZoomController = FakeZoomController()
  private val designSurface: DesignSurface<*> = mock { on { zoomController } doReturn fakeZoomController }
  private val navigationController: InteractivePreviewNavigationController = mock()
  private val selectedPreview: ComposePreviewElementInstance<*> =
    SingleComposePreviewElementInstance.forTesting<Unit>("composableMethodName")
  private val previewModeManager = TestComposePreviewManager().apply { setMode(PreviewMode.Interactive(selectedPreview)) }

  private val action = PredictiveBackNavigationControlsAction()

  @Before
  fun setUp() {
    // Default mock setup: initially navigation controls are hidden
    whenever(navigationController.isNavigationControlsShown()).thenReturn(false)
  }

  @Test
  fun `actionPerformed when bottom panel is shown and wasZoomToFit is true`() {
    // canZoomToFit is false, the surface is already in zoom to fit.
    fakeZoomController.canZoomToFitValue = false
    // Navigation bar is disabled.
    whenever(navigationController.isNavigationControlsShown()).thenReturn(false)

    // Action to show the navigation panel.
    val event = createEvent(designSurface, navigationController, previewModeManager)
    action.actionPerformed(event)

    // Ensure we show the navigation controls for the current Preview instance
    verify(navigationController).showNavigationControls(selectedPreview)

    // Ensure Zoom-to-fit settings are reset because the size of the surface will change, and we want to apply the zoom-to-fit to the new
    // size.
    assertThat(fakeZoomController.resetZoomToFitSettingsCalled).isTrue()
    assertThat(fakeZoomController.shouldWaitForResizeParam).isTrue()
    assertThat(fakeZoomController.shouldWaitForLayoutCreatedParam).isFalse()
    assertThat(fakeZoomController.zoomToFitCalled).isTrue()

    // Ensure we are still in zoom-to-fit mode.
    assertThat(fakeZoomController.canZoomToFit()).isFalse()
  }

  @Test
  fun `actionPerformed when bottom panel is shown and wasZoomToFit is false`() {
    // canZoomToFit is true, the surface is not in zoom to fit.
    fakeZoomController.canZoomToFitValue = true
    whenever(navigationController.isNavigationControlsShown()).thenReturn(false)

    val event = createEvent(designSurface, navigationController, previewModeManager)
    action.actionPerformed(event)

    // Ensure we show the navigation controls for the current Preview instance and zoomToFit is called.
    verify(navigationController).showNavigationControls(selectedPreview)

    // Ensure Zoom-to-fit functionalities are never called.
    assertThat(fakeZoomController.resetZoomToFitSettingsCalled).isFalse()
    assertThat(fakeZoomController.zoomToFitCalled).isFalse()

    // Ensure we are still NOT in zoom-to-fit mode
    assertThat(fakeZoomController.canZoomToFit()).isTrue()
  }

  @Test
  fun `actionPerformed when bottom panel changes to hidden and wasZoomToFit is true`() {
    // canZoomToFit is false, already in zoom to fit with bottom panel expanded
    fakeZoomController.canZoomToFitValue = false
    whenever(navigationController.isNavigationControlsShown()).thenReturn(true)

    val event = createEvent(designSurface, navigationController, previewModeManager)
    action.actionPerformed(event)

    // Ensure we hide the navigation controls.
    verify(navigationController).hideNavigationControls()

    // Ensure Zoom-to-fit settings are reset because the size of the surface will change, and we want to apply the zoom-to-fit to the new
    // size.
    assertThat(fakeZoomController.resetZoomToFitSettingsCalled).isTrue()
    assertThat(fakeZoomController.shouldWaitForResizeParam).isTrue()
    assertThat(fakeZoomController.shouldWaitForLayoutCreatedParam).isFalse()
    assertThat(fakeZoomController.zoomToFitCalled).isTrue()

    // Ensure we are still in zoom-to-fit mode.
    assertThat(fakeZoomController.canZoomToFit()).isFalse()
  }

  @Test
  fun `actionPerformed when bottom panel changes to hidden and wasZoomToFit is false`() {
    // canZoomToFit is true not in zoom to fit with bottom panel expanded.
    fakeZoomController.canZoomToFitValue = true
    whenever(navigationController.isNavigationControlsShown()).thenReturn(true)

    val event = createEvent(designSurface, navigationController, previewModeManager)
    action.actionPerformed(event)

    // Ensure we hide the navigation controls.
    verify(navigationController).hideNavigationControls()

    // Ensure Zoom-to-fit functionalities are never called.
    assertThat(fakeZoomController.resetZoomToFitSettingsCalled).isFalse()
    assertThat(fakeZoomController.zoomToFitCalled).isFalse()

    // Ensure we are still in zoom-to-fit mode.
    assertThat(fakeZoomController.canZoomToFit()).isTrue()
  }

  private fun createEvent(
    surface: DesignSurface<*>,
    navController: InteractivePreviewNavigationController,
    modeManager: PreviewModeManager,
  ): AnActionEvent {
    val dataContext =
      SimpleDataContext.builder()
        .add(DESIGN_SURFACE, surface)
        .add(InteractivePreviewNavigationController.KEY, navController)
        .add(PreviewModeManager.KEY, modeManager)
        .build()
    return TestActionEvent.createTestEvent(dataContext)
  }

  private class FakeZoomController : ZoomController {
    override val scale: Double = 1.0
    override val screenScalingFactor: Double = 1.0
    override var storeId: String? = null
    override val minScale: Double = 0.1
    override val maxScale: Double = 10.0

    var canZoomToFitValue: Boolean = true
    var resetZoomToFitSettingsCalled = false
    var shouldWaitForResizeParam: Boolean? = null
    var shouldWaitForLayoutCreatedParam: Boolean? = null
    var zoomToFitCalled = false

    override fun setScale(scale: Double, x: Int, y: Int) = true

    override fun zoomToFit(): Boolean {
      zoomToFitCalled = true
      return true
    }

    override fun getFitScale(): Double = 1.0

    override fun resetZoomToFitSettings(shouldWaitForResize: Boolean, shouldWaitForLayoutCreated: Boolean) {
      resetZoomToFitSettingsCalled = true
      shouldWaitForResizeParam = shouldWaitForResize
      shouldWaitForLayoutCreatedParam = shouldWaitForLayoutCreated
    }

    override fun zoom(type: ZoomType) = true

    override fun canZoomIn() = true

    override fun canZoomOut() = true

    override fun canZoomToFit() = canZoomToFitValue

    override fun canZoomToActual() = true
  }
}

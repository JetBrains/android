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

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.actions.prettyPrintActions
import com.android.tools.idea.actions.ColorBlindModeAction
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.common.surface.layout.EmptySurfaceLayoutManager
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.surface.ScreenViewProvider
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.testFramework.TestActionEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComposeViewControlActionTest {

  @JvmField @Rule val rule = AndroidProjectRule.inMemory().onEdt()

  @Before
  fun setup() {
    StudioFlags.COMPOSE_VIEW_FILTER.override(false)
    StudioFlags.COMPOSE_VIEW_INSPECTOR.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_VIEW_INSPECTOR.clearOverride()
    StudioFlags.COMPOSE_VIEW_FILTER.clearOverride()
    StudioFlags.COMPOSE_ZOOM_CONTROLS_DROPDOWN.clearOverride()
  }

  @Suppress("SpellCheckingInspection")
  @Test
  fun testZoomActionsWithFlagDisabled() {
    StudioFlags.COMPOSE_ZOOM_CONTROLS_DROPDOWN.override(false)
    val options =
      listOf(
        SurfaceLayoutOption("Layout A", EmptySurfaceLayoutManager()),
        SurfaceLayoutOption("Layout B", EmptySurfaceLayoutManager()),
        SurfaceLayoutOption("Layout C", EmptySurfaceLayoutManager()),
      )

    val viewControlAction =
      ComposeViewControlAction(options, additionalActionProvider = ColorBlindModeAction())

    val expected =
      """View Control
    Switch Layout
    Layout A
    Layout B
    Layout C
    ------------------------------------------------------
    Show Inspection Tooltips
    ------------------------------------------------------
    Color Blind Modes
        ✔ Original
        Protanopes
        Protanomaly
        Deuteranopes
        Deuteranomaly
        Tritanopes
        Tritanomaly
"""

    val designSurfaceMock = mock<NlDesignSurface>()
    whenever(designSurfaceMock.screenViewProvider).thenReturn(NlScreenViewProvider.RENDER)
    val dataContext = DataContext { if (DESIGN_SURFACE.`is`(it)) designSurfaceMock else null }

    val actionContent = prettyPrintActions(viewControlAction, dataContext = dataContext)
    assertEquals(expected, actionContent)
  }

  @Suppress("SpellCheckingInspection")
  @Test
  fun testZoomActionsWithFlagEnabled() {
    StudioFlags.COMPOSE_ZOOM_CONTROLS_DROPDOWN.override(true)
    val options =
      listOf(
        SurfaceLayoutOption("Layout A", EmptySurfaceLayoutManager()),
        SurfaceLayoutOption("Layout B", EmptySurfaceLayoutManager()),
        SurfaceLayoutOption("Layout C", EmptySurfaceLayoutManager()),
      )

    val viewControlAction =
      ComposeViewControlAction(options, additionalActionProvider = ColorBlindModeAction())

    val expected =
      """View Control
    Switch Layout
    Layout A
    Layout B
    Layout C
    ------------------------------------------------------
    Zoom In
    Zoom Out
    Zoom to 100%
    ------------------------------------------------------
    Show Inspection Tooltips
    ------------------------------------------------------
    Color Blind Modes
        ✔ Original
        Protanopes
        Protanomaly
        Deuteranopes
        Deuteranomaly
        Tritanopes
        Tritanomaly
"""

    val designSurfaceMock = mock<NlDesignSurface>()
    whenever(designSurfaceMock.screenViewProvider).thenReturn(NlScreenViewProvider.RENDER)
    val dataContext = DataContext { if (DESIGN_SURFACE.`is`(it)) designSurfaceMock else null }

    val actionContent = prettyPrintActions(viewControlAction, dataContext = dataContext)
    assertEquals(expected, actionContent)
  }

  @Suppress("SpellCheckingInspection")
  @Test
  fun testColorBlindModeIsSelectedBasedOnTheScreenViewProvider() {
    StudioFlags.COMPOSE_ZOOM_CONTROLS_DROPDOWN.override(true)
    val options =
      listOf(
        SurfaceLayoutOption("Layout A", EmptySurfaceLayoutManager()),
        SurfaceLayoutOption("Layout B", EmptySurfaceLayoutManager()),
        SurfaceLayoutOption("Layout C", EmptySurfaceLayoutManager()),
      )

    val viewControlAction =
      ComposeViewControlAction(options, additionalActionProvider = ColorBlindModeAction())

    val expected =
      """View Control
    Switch Layout
    Layout A
    Layout B
    Layout C
    ------------------------------------------------------
    Zoom In
    Zoom Out
    Zoom to 100%
    ------------------------------------------------------
    Show Inspection Tooltips
    ------------------------------------------------------
    Color Blind Modes
        Original
        Protanopes
        ✔ Protanomaly
        Deuteranopes
        Deuteranomaly
        Tritanopes
        Tritanomaly
"""

    val screenViewProviderMock = mock<ScreenViewProvider>()

    val designSurfaceMock = mock<NlDesignSurface>()
    whenever(designSurfaceMock.screenViewProvider).thenReturn(screenViewProviderMock)
    whenever(screenViewProviderMock.colorBlindFilter).thenReturn(ColorBlindMode.PROTANOMALY)
    val dataContext = DataContext { if (DESIGN_SURFACE.`is`(it)) designSurfaceMock else null }

    val actionContent = prettyPrintActions(viewControlAction, dataContext = dataContext)
    assertEquals(expected, actionContent)
  }

  @Test
  fun testNotEnabledWhenRefreshing() {
    val manager = TestComposePreviewManager()
    val refreshingStatus =
      ComposePreviewManager.Status(
        hasErrorsAndNeedsBuild = false,
        hasSyntaxErrors = false,
        isOutOfDate = false,
        isRefreshing = true,
        areResourcesOutOfDate = false,
        previewedFile = null,
      )
    val nonRefreshingStatus =
      ComposePreviewManager.Status(
        hasErrorsAndNeedsBuild = false,
        hasSyntaxErrors = false,
        isOutOfDate = false,
        isRefreshing = false,
        areResourcesOutOfDate = false,
        previewedFile = null,
      )
    val context = DataContext {
      when {
        PREVIEW_VIEW_MODEL_STATUS.`is`(it) -> manager.currentStatus
        else -> null
      }
    }
    val event = TestActionEvent.createTestEvent(context)
    val viewControlAction =
      ComposeViewControlAction(listOf(SurfaceLayoutOption("Layout A", EmptySurfaceLayoutManager())))

    manager.currentStatus = nonRefreshingStatus
    viewControlAction.update(event)
    assertTrue(event.presentation.isEnabled)

    manager.currentStatus = refreshingStatus
    viewControlAction.update(event)
    assertFalse(event.presentation.isEnabled)

    manager.currentStatus = nonRefreshingStatus
    viewControlAction.update(event)
    assertTrue(event.presentation.isEnabled)
  }

  @Suppress("UnstableApiUsage")
  @Test
  fun testNotMultiChoiceAction() {
    val option = listOf(SurfaceLayoutOption("Layout A", EmptySurfaceLayoutManager()))

    var enabled = true
    val action = ComposeViewControlAction(option, { enabled })
    val presentation = Presentation()

    // It should always not be multi-choice no matter it is enabled or not.
    action.update(TestActionEvent.createTestToolbarEvent(presentation))
    assertFalse(Utils.isMultiChoiceGroup(action))
    enabled = false
    action.update(TestActionEvent.createTestToolbarEvent(presentation))
    assertFalse(Utils.isMultiChoiceGroup(action))
  }
}

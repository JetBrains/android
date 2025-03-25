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
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
  }

  @Suppress("SpellCheckingInspection")
  @Test
  fun testZoomActions() {
    val options =
      listOf(
        SurfaceLayoutOption("Layout A", { EmptySurfaceLayoutManager() }),
        SurfaceLayoutOption("Layout B", { EmptySurfaceLayoutManager() }),
        SurfaceLayoutOption("Layout C", { EmptySurfaceLayoutManager() }),
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
    whenever(designSurfaceMock.colorBlindMode).thenReturn(ColorBlindMode.NONE)
    val dataContext = SimpleDataContext.getSimpleContext(DESIGN_SURFACE, designSurfaceMock)

    val actionContent = prettyPrintActions(viewControlAction, dataContext = dataContext)
    assertEquals(expected, actionContent)
  }

  @Suppress("SpellCheckingInspection")
  @Test
  fun testColorBlindModeIsSelectedBasedOnTheScreenViewProvider() {
    val options =
      listOf(
        SurfaceLayoutOption("Layout A", { EmptySurfaceLayoutManager() }),
        SurfaceLayoutOption("Layout B", { EmptySurfaceLayoutManager() }),
        SurfaceLayoutOption("Layout C", { EmptySurfaceLayoutManager() }),
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
        Original
        Protanopes
        ✔ Protanomaly
        Deuteranopes
        Deuteranomaly
        Tritanopes
        Tritanomaly
"""

    val designSurfaceMock = mock<NlDesignSurface>()
    whenever(designSurfaceMock.colorBlindMode).thenReturn(ColorBlindMode.PROTANOMALY)
    val dataContext = SimpleDataContext.getSimpleContext(DESIGN_SURFACE, designSurfaceMock)

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
        psiFilePointer = null,
      )
    val nonRefreshingStatus =
      ComposePreviewManager.Status(
        hasErrorsAndNeedsBuild = false,
        hasSyntaxErrors = false,
        isOutOfDate = false,
        isRefreshing = false,
        areResourcesOutOfDate = false,
        psiFilePointer = null,
      )
    lateinit var event: AnActionEvent
    val viewControlAction =
      ComposeViewControlAction(
        listOf(SurfaceLayoutOption("Layout A", { EmptySurfaceLayoutManager() }))
      )

    fun ComposePreviewManager.Status.setAndUpdate() {
      manager.currentStatus =
        this.also {
          event =
            TestActionEvent.createTestEvent(
              SimpleDataContext.getSimpleContext(PREVIEW_VIEW_MODEL_STATUS, this)
            )
          viewControlAction.update(event)
        }
    }

    nonRefreshingStatus.setAndUpdate()
    assertTrue(event.presentation.isEnabled)

    refreshingStatus.setAndUpdate()
    assertFalse(event.presentation.isEnabled)

    nonRefreshingStatus.setAndUpdate()
    assertTrue(event.presentation.isEnabled)
  }

  @Test
  fun testNotVisibleIfNoActionsAvailable() {
    StudioFlags.COMPOSE_VIEW_FILTER.override(false)
    StudioFlags.COMPOSE_VIEW_INSPECTOR.override(false)
    val event = createAndUpdateEvent()
    assertFalse(event.presentation.isVisible)
  }

  @Test
  fun testVisibleIfFilterActionAvailable() {
    StudioFlags.COMPOSE_VIEW_FILTER.override(true)
    StudioFlags.COMPOSE_VIEW_INSPECTOR.override(false)
    val event = createAndUpdateEvent()
    assertTrue(event.presentation.isVisible)
  }

  @Test
  fun testVisibleIfInspectorActionAvailable() {
    StudioFlags.COMPOSE_VIEW_FILTER.override(false)
    StudioFlags.COMPOSE_VIEW_INSPECTOR.override(true)
    val event = createAndUpdateEvent()
    assertTrue(event.presentation.isVisible)
  }

  @Test
  fun testVisibleIfAdditionalActionAvailable() {
    StudioFlags.COMPOSE_VIEW_FILTER.override(false)
    StudioFlags.COMPOSE_VIEW_INSPECTOR.override(false)
    val event = createAndUpdateEvent(ColorBlindModeAction())
    assertTrue(event.presentation.isVisible)
  }

  private fun createAndUpdateEvent(
    additionalActionProvider: ColorBlindModeAction? = null
  ): AnActionEvent {
    val viewControlAction =
      ComposeViewControlAction(emptyList(), additionalActionProvider = additionalActionProvider)
    val dataContext = SimpleDataContext.getSimpleContext(DESIGN_SURFACE, mock<NlDesignSurface>())
    val event = TestActionEvent.createTestEvent(dataContext)
    viewControlAction.update(event)
    return event
  }
}

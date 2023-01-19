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
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.Zoomable
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.actions.prettyPrintActions
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.layout.EmptySurfaceLayoutManager
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.uibuilder.actions.LayoutManagerSwitcher
import com.android.tools.idea.uibuilder.actions.SurfaceLayoutManagerOption
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManager
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
    StudioFlags.COMPOSE_COLORBLIND_MODE.override(true)
    StudioFlags.COMPOSE_VIEW_INSPECTOR.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_VIEW_INSPECTOR.clearOverride()
    StudioFlags.COMPOSE_COLORBLIND_MODE.clearOverride()
    StudioFlags.COMPOSE_VIEW_FILTER.clearOverride()
  }

  @Suppress("SpellCheckingInspection")
  @Test
  fun testZoomActions() {
    val options =
      listOf(
        createOption("Layout A", EmptySurfaceLayoutManager()),
        createOption("Layout B", EmptySurfaceLayoutManager()),
        createOption("Layout C", EmptySurfaceLayoutManager())
      )

    val context = DataContext {
      when {
        ZOOMABLE_KEY.`is`(it) -> TestZoomable()
        DESIGN_SURFACE.`is`(it) -> mock<NlDesignSurface>()
        else -> null
      }
    }

    val viewControlAction = ComposeViewControlAction(EmptyLayoutManagerSwitcher, options)
    viewControlAction.updateActions(context)

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
        Protanopes
        Protanomaly
        Deuteranopes
        Deuteranomaly
        Tritanopes
"""

    val actionContent = prettyPrintActions(viewControlAction)
    assertEquals(expected, actionContent)
  }

  @Test
  fun testNotEnabledWhenRefreshing() {
    val manager = TestComposePreviewManager()
    val refreshingStatus =
      ComposePreviewManager.Status(
        hasRuntimeErrors = false,
        hasSyntaxErrors = false,
        isOutOfDate = false,
        isRefreshing = true,
        areResourcesOutOfDate = false,
        interactiveMode = ComposePreviewManager.InteractiveMode.DISABLED
      )
    val nonRefreshingStatus =
      ComposePreviewManager.Status(
        hasRuntimeErrors = false,
        hasSyntaxErrors = false,
        isOutOfDate = false,
        isRefreshing = false,
        areResourcesOutOfDate = false,
        interactiveMode = ComposePreviewManager.InteractiveMode.DISABLED
      )
    val context = DataContext {
      when {
        COMPOSE_PREVIEW_MANAGER.`is`(it) -> manager
        else -> null
      }
    }
    val event = TestActionEvent(context)
    val viewControlAction =
      ComposeViewControlAction(
        EmptyLayoutManagerSwitcher,
        listOf(createOption("Layout A", EmptySurfaceLayoutManager()))
      )

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
    val switcher =
      object : LayoutManagerSwitcher {
        override fun isLayoutManagerSelected(layoutManager: SurfaceLayoutManager): Boolean = true

        override fun setLayoutManager(
          layoutManager: SurfaceLayoutManager,
          sceneViewAlignment: DesignSurface.SceneViewAlignment
        ) = Unit
      }
    val option = listOf(SurfaceLayoutManagerOption("Layout A", EmptySurfaceLayoutManager()))

    var enabled = true
    val action = ComposeViewControlAction(switcher, option) { enabled }
    val presentation = Presentation()

    // It should always not be multi-choice no matter it is enabled or not.
    action.update(TestActionEvent(presentation))
    assertFalse(Utils.isMultiChoiceGroup(action))
    enabled = false
    action.update(TestActionEvent(presentation))
    assertFalse(Utils.isMultiChoiceGroup(action))
  }
}

private class TestZoomable : Zoomable {
  override val scale: Double = 1.0
  override val screenScalingFactor = 1.0
  override fun zoom(type: ZoomType): Boolean = true

  override fun canZoomIn(): Boolean = true

  override fun canZoomOut(): Boolean = true

  override fun canZoomToFit(): Boolean = true

  override fun canZoomToActual(): Boolean = true
}

private object EmptyLayoutManagerSwitcher : LayoutManagerSwitcher {

  override fun isLayoutManagerSelected(layoutManager: SurfaceLayoutManager): Boolean = true

  override fun setLayoutManager(
    layoutManager: SurfaceLayoutManager,
    sceneViewAlignment: DesignSurface.SceneViewAlignment
  ) = Unit
}

private fun createOption(
  displayText: String,
  layoutManager: SurfaceLayoutManager
): SurfaceLayoutManagerOption {
  return SurfaceLayoutManagerOption(displayText, layoutManager)
}

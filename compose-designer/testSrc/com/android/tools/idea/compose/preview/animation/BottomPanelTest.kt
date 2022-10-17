/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import com.android.SdkConstants
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.components.JBLabel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class BottomPanelTest(private val enableCoordinationDrag: Boolean, private val isCoordinationPanelOpened: Boolean) {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var surface: DesignSurface<*>

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "Coordination drag is enabled: {0}, coordination panel opened: {1}")
    fun parameters() = listOf(arrayOf<Any>(true, true), arrayOf<Any>(true, false), arrayOf<Any>(false, true), arrayOf<Any>(false, false))
  }

  @Before
  fun setUp() {
    val model = runInEdtAndGet {
      NlModelBuilderUtil.model(
        projectRule,
        "layout",
        "layout.xml",
        ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER)
      ).build()
    }
    surface = NlDesignSurface.builder(projectRule.project, projectRule.fixture.testRootDisposable).build()
    surface.addModelWithoutRender(model)
    StudioFlags.COMPOSE_ANIMATION_PREVIEW_COORDINATION_DRAG.override(enableCoordinationDrag)
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_ANIMATION_PREVIEW_COORDINATION_DRAG.clearOverride()
  }

  private val minimumSize = Dimension(10, 10)

  @Test
  fun `reset button is visible and clickable`(): Unit = invokeAndWaitIfNeeded {
    if (!enableCoordinationDrag) return@invokeAndWaitIfNeeded
    val panel = createBottomPanel()
    val ui = FakeUi(panel.parent).apply {
      updateToolbars()
      layout()
    }
    (panel.components[0] as Container).components[2].also {
      // Reset button.
      assertTrue(it.isVisible)
      assertTrue(it.isEnabled)
      TestUtils.assertBigger(minimumSize, it.size)
      // After clicking button callback is called.
      var resetCalls = 0
      panel.addResetListener { resetCalls++ }
      ui.clickOn(it)
      ui.updateToolbars()
      assertEquals(1, resetCalls)
    }
  }

  @Test
  fun `reset button is disabled if coordination is not available`(): Unit = invokeAndWaitIfNeeded {
    if (!enableCoordinationDrag) return@invokeAndWaitIfNeeded
    val panel = createBottomPanel(false)
    val ui = FakeUi(panel.parent).apply {
      updateToolbars()
      layout()
    }
    (panel.components[0] as Container).components[2].also {
      // Reset button.
      assertTrue(it.isVisible)
      assertFalse(it.isEnabled)
      TestUtils.assertBigger(minimumSize, it.size)
    }
  }


  @Test
  fun `no reset button if coordination drag is not available`(): Unit = invokeAndWaitIfNeeded {
    if (enableCoordinationDrag) return@invokeAndWaitIfNeeded
    val panel = createBottomPanel()
    val ui = FakeUi(panel.parent).apply {
      updateToolbars()
      layout()
    }
    assertEquals(1, (panel.components[0] as Container).components.size)
  }


  @Test
  fun `label is visible`(): Unit = invokeAndWaitIfNeeded {
    val panel = createBottomPanel().apply {
      clockTimeMs = 1234
    }
    FakeUi(panel.parent).apply {
      updateToolbars()
      layout()
    }
    val labelComponent = (panel.components[0] as Container).components[0]
    assertTrue(labelComponent.isVisible)
    TestUtils.assertBigger(minimumSize, labelComponent.size)
    panel.clockTimeMs = 1234567890
    TestUtils.assertBigger(Dimension(40, 10), labelComponent.size)

  }

  @Test
  fun `label is updated immediately`(): Unit = invokeAndWaitIfNeeded {
    val panel = createBottomPanel().apply {
      clockTimeMs = 1234
    }
    FakeUi(panel.parent).apply {
      updateToolbars()
      layout()
    }
    val labelComponent = (panel.components[0] as Container).components[0] as JBLabel
    assertEquals("1234 ms", labelComponent.text)
    panel.clockTimeMs = 100
    assertEquals("100 ms", labelComponent.text)
  }

  @Test
  fun `ui preview renders correctly`(): Unit = invokeAndWaitIfNeeded {
    val panel = createBottomPanel().apply {
      clockTimeMs = 1234
    }
    FakeUi(panel.parent).apply {
      updateToolbars()
      layout()
      // Uncomment to preview ui.
      //render()
    }
  }


  /** Create [BottomPanel] with 300x500 size. */
  private fun createBottomPanel(withCoordination: Boolean = true): BottomPanel {
    val panel = BottomPanel(object : AnimationPreviewState {
      override fun isCoordinationAvailable() = withCoordination
      override fun isCoordinationPanelOpened() = isCoordinationPanelOpened
    }, surface) {}
    JPanel(BorderLayout()).apply {
      setSize(300, 500)
      add(panel, BorderLayout.SOUTH)
    }
    return panel
  }
}
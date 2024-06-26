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
package com.android.tools.idea.preview.animation

import com.android.SdkConstants
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.preview.NoopAnimationTracker
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import com.intellij.testFramework.runInEdtAndGet
import java.awt.Dimension
import javax.swing.JSlider
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlaybackControlsTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var surface: com.android.tools.idea.common.surface.DesignSurface<*>

  private lateinit var parentDisposable: Disposable

  @Before
  fun setUp() {
    parentDisposable = projectRule.fixture.testRootDisposable
    val model = runInEdtAndGet {
      com.android.tools.idea.uibuilder.NlModelBuilderUtil.model(
          projectRule,
          "layout",
          "layout.xml",
          com.android.tools.idea.common.fixtures.ComponentDescriptor(
            SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER
          ),
        )
        .build()
    }
    surface =
      com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder.builder(
          projectRule.project,
          parentDisposable,
        )
        .build()
    surface.addModelWithoutRender(model)
  }

  private val minimumSize = Dimension(10, 10)

  private class TestAction : ToolbarLabelAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.text = "Label"
    }
  }

  @Test
  fun `create toolbar and each component is visible`() =
    runBlocking(uiThread) {
      val playbackControl =
        PlaybackControls(
          clockControl = SliderClockControl(JSlider()),
          NoopAnimationTracker,
          surface,
          parentDisposable,
        )
      val toolbar = playbackControl.createToolbar().apply { setSize(300, 50) }
      val ui =
        FakeUi(toolbar).apply {
          updateToolbars()
          layout()
        }
      // Uncomment to preview ui.
      // ui.render()
      Assert.assertEquals(5, toolbar.components.size)
      toolbar.components.forEach { TestUtils.assertBigger(minimumSize, it.size) }
    }

  @Test
  fun `create toolbar with extra action and each component is visible`() =
    runBlocking(uiThread) {
      val playbackControl =
        PlaybackControls(
          clockControl = SliderClockControl(JSlider()),
          NoopAnimationTracker,
          surface,
          parentDisposable,
        )
      val toolbar =
        playbackControl.createToolbar(listOf(TestAction(), TestAction())).apply { setSize(600, 50) }
      val ui =
        com.android.tools.adtui.swing.FakeUi(toolbar).apply {
          updateToolbars()
          layout()
        }
      // Uncomment to preview ui.
      // ui.render()
      // Two extra actions and one separator.
      Assert.assertEquals(8, toolbar.components.size)
      toolbar.components.forEachIndexed { index, it ->
        // Don't check Separator size as it is smaller.
        if (index != 5) TestUtils.assertBigger(minimumSize, it.size)
      }
    }
}

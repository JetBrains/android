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
import com.android.tools.idea.compose.preview.animation.timeline.ElementState
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals

class AllTabPanelTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var parentDisposable: Disposable

  private lateinit var surface: DesignSurface<*>

  @Before
  fun setUp() {
    parentDisposable = Disposer.newDisposable()
    val model = runInEdtAndGet {
      NlModelBuilderUtil.model(
        projectRule,
        "layout",
        "layout.xml",
        ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER)
      ).build()
    }
    surface = NlDesignSurface.builder(projectRule.project, parentDisposable).build()
    surface.addModelWithoutRender(model)
  }

  @After
  fun tearDown() {
    Disposer.dispose(parentDisposable)
  }

  @Test
  fun `add playback`() {
    val panel = AllTabPanel().apply { setSize(1000, 800) }
    panel.addPlayback(JPanel().apply {
      background = JBColor.blue
    })
  }

  @Test
  fun `add timeline`() {
    val panel = AllTabPanel().apply { setSize(1000, 800) }
    panel.addTimeline(JPanel().apply {
      background = JBColor.blue
    })
  }

  @Test
  fun `add and remove cards`() {
    val cardOne = AnimationCard(TestUtils.testPreviewState(), surface, ElementState("One")) {}.apply { setDuration(111) }
    val cardTwo = AnimationCard(TestUtils.testPreviewState(), surface, ElementState("Two")) {}.apply { setDuration(222) }
    val cardThree = AnimationCard(TestUtils.testPreviewState(), surface, ElementState("Three")) {}.apply { setDuration(333) }

    val panel = AllTabPanel().apply { setSize(1000, 800) }

    invokeAndWaitIfNeeded {
      assertEquals(0, panel.getNumberOfCards())
      panel.addCard(cardOne)
      assertEquals(1, panel.getNumberOfCards())
      panel.addCard(cardTwo)
      assertEquals(2, panel.getNumberOfCards())
      // Can't add the same card twice
      panel.addCard(cardTwo)
      assertEquals(2, panel.getNumberOfCards())
      panel.addCard(cardThree)
      assertEquals(3, panel.getNumberOfCards())
      panel.removeCard(cardTwo)
      assertEquals(2, panel.getNumberOfCards())
      // Can't remove the same card twice
      panel.removeCard(cardTwo)
      assertEquals(2, panel.getNumberOfCards())
      panel.removeCard(cardThree)
      assertEquals(1, panel.getNumberOfCards())
      //It's okay to add removed card again.
      panel.addCard(cardThree)
      assertEquals(2, panel.getNumberOfCards())
      //Remove all remaining cards.
      panel.removeCard(cardThree)
      panel.removeCard(cardOne)
      assertEquals(0, panel.getNumberOfCards())
      // All cards are already removed.
      panel.removeCard(cardThree)
    }
  }

  @Test
  fun `preview ui`() {
    val cardOne = AnimationCard(TestUtils.testPreviewState(), surface, ElementState("One")) {}.apply { setDuration(111) }
    val cardTwo = AnimationCard(TestUtils.testPreviewState(), surface, ElementState("Two")) {}.apply { setDuration(222) }
    val cardThree = AnimationCard(TestUtils.testPreviewState(), surface, ElementState("Three")) {}.apply { setDuration(333) }


    val panel = AllTabPanel().apply {
      setSize(1000, 800)
      addPlayback(JLabel("Playback placeholder").apply {
        background = JBColor.blue
      })
      addTimeline(JLabel("Timeline placeholder").apply {
        background = JBColor.green
      })
    }

    invokeAndWaitIfNeeded {
      val ui = FakeUi(panel)
      panel.addCard(cardOne)
      panel.addCard(cardTwo)
      panel.addCard(cardThree)
      ui.updateToolbars()
      ui.layout()
      ui.render()
    }
  }

  @Test
  fun `scroll ui`() {
    val panel = AllTabPanel().apply {
      setSize(1000, 400)
      addPlayback(JLabel("Playback placeholder").apply {
        background = JBColor.blue
      })
      addTimeline(JLabel("Timeline placeholder").apply {
        background = JBColor.green
      })
    }
    for (i in 0..10) {
      panel.addCard(AnimationCard(TestUtils.testPreviewState(), surface, ElementState("card $i")) {}.apply { setDuration(i * 10) })
    }

    invokeAndWaitIfNeeded {
      val ui = FakeUi(panel)
      ui.updateToolbars()
      ui.layout()
      ui.render()
      ui.mouse.wheel(200, 200, 100)
      //TODO Check if scroll works properly
      ui.render()
    }
  }

  private fun JPanel.getNumberOfCards() =
    ((this.components[0] as JBScrollPane).viewport.components[0] as JBSplitter).firstComponent.components.count()

}
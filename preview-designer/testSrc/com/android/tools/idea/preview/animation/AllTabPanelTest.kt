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
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.preview.NoopAnimationTracker
import com.android.tools.idea.preview.animation.AllTabPanel.Companion.createAllTabPanelForTest
import com.android.tools.idea.preview.animation.TestUtils.createTestSlider
import com.android.tools.idea.preview.animation.TestUtils.findExpandButton
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AllTabPanelTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var surface: DesignSurface<*>

  private lateinit var panel: AllTabPanel

  @Before
  fun setUp() {
    val model = runInEdtAndGet {
      NlModelBuilderUtil.model(
          projectRule,
          "layout",
          "layout.xml",
          ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER),
        )
        .build()
    }
    surface = NlSurfaceBuilder.builder(projectRule.project, projectRule.testRootDisposable).build()
    surface.addModelWithoutRender(model)
    panel = AllTabPanel(projectRule.testRootDisposable)
  }

  @Test
  fun `add playback`() {
    panel.apply { setSize(1000, 800) }
    panel.addPlayback(JPanel().apply { background = JBColor.blue })
  }

  @Test
  fun `add timeline`() {
    panel.apply { setSize(1000, 800) }
    panel.addTimeline(JPanel().apply { background = JBColor.blue })
  }

  @Test
  fun `add and remove cards`() {
    val slider = createTestSlider()
    val cardOne =
      AnimationCard(surface, "One", emptyList(), NoopAnimationTracker).apply { setDuration(111) }
    val cardTwo =
      AnimationCard(surface, "Two", emptyList(), NoopAnimationTracker).apply { setDuration(222) }
    val cardThree =
      AnimationCard(surface, "Three", emptyList(), NoopAnimationTracker).apply { setDuration(333) }

    panel.apply { setSize(1000, 800) }

    ApplicationManager.getApplication().invokeAndWait {
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
      // It's okay to add removed card again.
      panel.addCard(cardThree)
      assertEquals(2, panel.getNumberOfCards())
      // Remove all remaining cards.
      panel.removeCard(cardThree)
      panel.removeCard(cardOne)
      assertEquals(0, panel.getNumberOfCards())
      // All cards are already removed.
      panel.removeCard(cardThree)
    }
  }

  @Test
  fun `add and remove label cards`() {
    val cardOne = LabelCard("One")
    val cardTwo = LabelCard("Two")
    val cardThree = LabelCard("Three")

    panel.apply { setSize(1000, 800) }

    ApplicationManager.getApplication().invokeAndWait {
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
      // It's okay to add removed card again.
      panel.addCard(cardThree)
      assertEquals(2, panel.getNumberOfCards())
      // Remove all remaining cards.
      panel.removeCard(cardThree)
      panel.removeCard(cardOne)
      assertEquals(0, panel.getNumberOfCards())
      // All cards are already removed.
      panel.removeCard(cardThree)
    }
  }

  @Test
  fun `preview ui`() {
    val slider = createTestSlider()
    val cardOne =
      AnimationCard(surface, "One", emptyList(), NoopAnimationTracker).apply { setDuration(111) }
    val cardTwo =
      AnimationCard(surface, "Two", emptyList(), NoopAnimationTracker).apply { setDuration(222) }
    val cardThree =
      AnimationCard(surface, "Three", emptyList(), NoopAnimationTracker).apply { setDuration(333) }

    panel.apply {
      setSize(1000, 800)
      addPlayback(TestUtils.createPlaybackPlaceHolder())
      addTimeline(TestUtils.createTimelinePlaceHolder())
    }

    ApplicationManager.getApplication().invokeAndWait {
      val ui = FakeUi(panel)
      panel.addCard(cardOne)
      panel.addCard(cardTwo)
      panel.addCard(cardThree)
      ui.updateToolbars()
      ui.layout()
      // Uncomment to preview.
      // ui.render()
    }
  }

  @Test
  fun `preview ui with mixed cards`() {
    val slider = createTestSlider()
    val cardOne =
      AnimationCard(surface, "AnimationCard One", emptyList(), NoopAnimationTracker).apply {
        setDuration(111)
      }
    val cardTwo =
      AnimationCard(surface, "AnimationCard Two", emptyList(), NoopAnimationTracker).apply {
        setDuration(222)
      }
    val cardThree =
      AnimationCard(surface, "AnimationCard Three", emptyList(), NoopAnimationTracker).apply {
        setDuration(333)
      }
    val labelCardOne = LabelCard("LabelCard One")
    val labelCardTwo = LabelCard("LabelCard Two")
    val labelCardThree = LabelCard("LabelCard Three)")

    panel.apply {
      setSize(1000, 800)
      addPlayback(TestUtils.createPlaybackPlaceHolder())
      addTimeline(TestUtils.createTimelinePlaceHolder())
    }

    ApplicationManager.getApplication().invokeAndWait {
      val ui = FakeUi(panel)
      panel.addCard(cardOne)
      panel.addCard(labelCardOne)
      panel.addCard(cardTwo)
      panel.addCard(labelCardTwo)
      panel.addCard(cardThree)
      panel.addCard(labelCardThree)
      ui.updateToolbars()
      ui.layout()
      // Uncomment to preview ui.
      // ui.render()
    }
  }

  @Test
  fun `expand cards`() {
    val slider = createTestSlider()
    panel.apply {
      setSize(1000, 400)
      addPlayback(TestUtils.createPlaybackPlaceHolder())
      addTimeline(TestUtils.createTimelinePlaceHolder())
    }
    for (i in 0..10) {
      panel.addCard(
        AnimationCard(surface, "card $i", emptyList(), NoopAnimationTracker).apply {
          setDuration(i * 10)
        }
      )
    }

    ApplicationManager.getApplication().invokeAndWait {
      val ui = FakeUi(panel)
      ui.updateToolbars()
      ui.layoutAndDispatchEvents()
      // Uncomment to preview.
      // ui.render()
      val firstCard = TestUtils.findAllCards(panel)[0] as AnimationCard
      firstCard.expandedSize = 300
      assertNotEquals(300, firstCard.getCurrentHeight())
      ui.clickOn(firstCard.findExpandButton())
      assertEquals(300, firstCard.getCurrentHeight())
      // Update card size without expanding.
      firstCard.expandedSize = 400
      panel.updateCardSize(firstCard)
      assertEquals(400, firstCard.getCurrentHeight())
    }
  }

  @Test
  fun `scroll ui`() {
    val slider = createTestSlider()
    panel.apply {
      setSize(1000, 400)
      addPlayback(TestUtils.createPlaybackPlaceHolder())
      addTimeline(TestUtils.createTimelinePlaceHolder())
    }
    for (i in 0..10) {
      panel.addCard(
        AnimationCard(surface, "card $i", emptyList(), NoopAnimationTracker).apply {
          setDuration(i * 10)
        }
      )
    }

    ApplicationManager.getApplication().invokeAndWait {
      val ui = FakeUi(panel)
      ui.updateToolbars()
      ui.layout()
      ui.render()
      ui.mouse.wheel(200, 200, 100)
      // TODO Check if scroll works properly
      ui.render()
    }
  }

  @Test
  fun `userScaleChangeListener cleared on disposal`() {
    val originalFactor = JBUIScale.scale(1f) // 1 * userScaleFactor
    var listenerTriggeredCount = 0
    val disposable = Disposer.newDisposable()

    panel = createAllTabPanelForTest(disposable) { listenerTriggeredCount++ }
    assertEquals(0, listenerTriggeredCount)

    // Change the user scale factor and make sure the listener is triggered
    JBUIScale.setUserScaleFactorForTest(2f)
    assertEquals(1, listenerTriggeredCount)

    // Change the user scale factor again to double-check the listener is triggered multiple times
    JBUIScale.setUserScaleFactorForTest(3f)
    assertEquals(2, listenerTriggeredCount)

    // Dispose the AllTabsPanel parent
    Disposer.dispose(disposable)
    // Change the user scale factor again, but at this point the listener should have been removed,
    // so it's not expected to be triggered again.
    JBUIScale.setUserScaleFactorForTest(4f)
    assertEquals(2, listenerTriggeredCount)

    JBUIScale.setUserScaleFactorForTest(originalFactor)
  }

  private fun JPanel.getNumberOfCards() =
    ((this.components[0] as JBScrollPane).viewport.components[0] as JBSplitter)
      .firstComponent
      .components
      .count()
}

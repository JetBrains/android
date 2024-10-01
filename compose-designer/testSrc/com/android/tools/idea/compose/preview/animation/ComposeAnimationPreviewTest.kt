/*
 * Copyright (C) 2020 The Android Open Source Project
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

import androidx.compose.animation.tooling.ComposeAnimation
import androidx.compose.animation.tooling.ComposeAnimationType
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.compose.preview.animation.TestUtils.createComposeAnimation
import com.android.tools.idea.compose.preview.animation.TestUtils.findComboBox
import com.android.tools.idea.compose.preview.animation.TestUtils.findLabel
import com.android.tools.idea.compose.preview.animation.managers.ComposeAnimationManager
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.preview.animation.AnimationCard
import com.android.tools.idea.preview.animation.LabelCard
import com.android.tools.idea.preview.animation.TestUtils.findAllCards
import com.android.tools.idea.preview.animation.TestUtils.findToolbar
import com.android.tools.idea.preview.animation.UnsupportedAnimationManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertInstanceOf
import com.intellij.util.containers.getIfSingle
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.util.stream.Collectors
import javax.swing.JComponent
import javax.swing.JSlider
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

class ComposeAnimationPreviewTest : InspectorTests() {

  private val animations =
    ComposeAnimationType.values().map { createComposeAnimation(it.toString(), type = it) }

  private suspend fun subscribeAnimations(animations: List<ComposeAnimation>) {
    surface.sceneManagers.forEach { it.requestRenderAsync().await() }
    animations.forEach { animationPreview.addAnimation(it).join() }
  }

  private suspend fun createFakeUiForInspector(inspector: ComposeAnimationPreview) =
    withContext(uiThread) {
      FakeUi(inspector.component.apply { size = Dimension(500, 400) }).apply {
        updateToolbars()
        layoutAndDispatchEvents()
      }
    }

  private fun createTransitionAnimation(
    states: Set<AnimationState> = setOf(AnimationState.State1, AnimationState.State2)
  ): ComposeAnimation {
    return object : ComposeAnimation {
      override val animationObject =
        object {
          @Suppress("unused") // Method is called via reflection.
          fun getCurrentState() = AnimationState.State1
        }
      override val type = ComposeAnimationType.TRANSITION_ANIMATION
      override val states = states
    }
  }

  @Test
  fun noAnimationsPanelShownWhenNoAnimationsAreSubscribed() = runBlocking {
    UIUtil.pump() // Wait for UI to dispatch all events
    // When first opening the inspector, we show the panel informing there are no supported
    // animations to be displayed
    assertNotNull(animationPreview.noAnimationsPanel())
    assertNull(animationPreview.tabbedPane.parent)
    assertEquals(0, animationPreview.animations.size)

    // After subscribing an animation, we should display the tabbedPane
    val animation = createComposeAnimation()
    animationPreview.addAnimation(animation).join()
    assertNull(animationPreview.noAnimationsPanel())
    assertNotNull(animationPreview.tabbedPane.parent)
    assertEquals(1, animationPreview.animations.size)

    // After subscribing an animation, we should display the tabbedPane
    val anotherAnimation = createComposeAnimation()
    animationPreview.addAnimation(anotherAnimation).join()
    assertNull(animationPreview.noAnimationsPanel())
    assertNotNull(animationPreview.tabbedPane.parent)
    assertEquals(2, animationPreview.animations.size)

    // After unsubscribing from one animation, animation panel still shown.
    animationPreview.removeAnimation(animation).join()
    assertNull(animationPreview.noAnimationsPanel())
    assertNotNull(animationPreview.tabbedPane.parent)
    assertEquals(1, animationPreview.animations.size)

    // After unsubscribing all animations, we should hide the tabbed panel and again display the no
    // animations panel
    animationPreview.removeAnimation(anotherAnimation).join()
    assertNotNull(animationPreview.noAnimationsPanel())
    assertNull(animationPreview.tabbedPane.parent)
    assertEquals(0, animationPreview.animations.size)

    // After subscribing again to the animation, we should display the tabbedPane
    animationPreview.addAnimation(animation).join()
    assertNull(animationPreview.noAnimationsPanel())
    assertNotNull(animationPreview.tabbedPane.parent)
    assertEquals(1, animationPreview.animations.size)
  }

  @Test
  fun oneTabPerSubscribedAnimation() = runBlocking {
    assertNull(animationPreview.tabbedPane.parent)
    assertEquals(0, animationPreview.animations.size)

    val animation1 = createComposeAnimation()
    animationPreview.addAnimation(animation1).join()
    assertNotNull(animationPreview.tabbedPane.parent)
    assertEquals(1, animationPreview.animations.size)

    val animation2 = createComposeAnimation()
    animationPreview.addAnimation(animation2).join()
    assertEquals(2, animationPreview.animations.size)

    animationPreview.removeAnimation(animation1).join()
    assertEquals(1, animationPreview.animations.size)
  }

  @Test
  fun comboBoxesDisplayComposeAnimationStates() = runBlocking {
    val animationStates = setOf(AnimationState.State1, AnimationState.State2, AnimationState.State3)
    val transitionAnimation = createTransitionAnimation(animationStates)

    subscribeAnimations(listOf(transitionAnimation))

    val ui = createFakeUiForInspector(animationPreview)

    // Find the AnimationCard toolbar
    val animationCardToolbar = animationPreview.component.findToolbar("AnimationCard") as JComponent

    // Assertions
    assertEquals(5, animationCardToolbar.componentCount)
    assertEquals("State1", animationCardToolbar.components[2].findComboBox().text) // From state
    assertEquals("State2", animationCardToolbar.components[4].findComboBox().text) // To state

    withContext(uiThread) {
      // Simulate clicking the "Swap" button
      ui.clickOn(animationCardToolbar.components[1])
    }

    // Check if states have been swapped
    assertEquals("State2", animationCardToolbar.components[2].findComboBox().text) // From state
    assertEquals("State1", animationCardToolbar.components[4].findComboBox().text) // To state
  }

  @Test
  fun animatedVisibilityComboBoxDisplayAllVisibilityStates() = runBlocking {
    val animatedVisibilityAnimation =
      object : ComposeAnimation {
        override val animationObject = Any()
        override val type = ComposeAnimationType.ANIMATED_VISIBILITY
        override val states = setOf("Enter", "Exit")
      }

    subscribeAnimations(listOf(animatedVisibilityAnimation))

    val ui = createFakeUiForInspector(animationPreview)

    // Find the AnimationCard toolbar
    val animationCardToolbar = animationPreview.component.findToolbar("AnimationCard") as JComponent

    // Assertions
    assertEquals(3, animationCardToolbar.componentCount)
    assertEquals("Enter", animationCardToolbar.components[2].findComboBox().text) // Initial state

    withContext(uiThread) {
      // Simulate clicking the "Swap" button
      ui.clickOn(animationCardToolbar.components[1])
    }

    // Check if state has changed
    assertEquals("Exit", animationCardToolbar.components[2].findComboBox().text)
  }

  @Test
  fun changeClockTime() = runBlocking {
    subscribeAnimations(listOf(createTransitionAnimation()))

    withContext(uiThread) {
      // We can get any of the combo boxes, since "from" and "to" states should be the same.
      val sliders =
        TreeWalker(animationPreview.component)
          .descendantStream()
          .filter { it is JSlider }
          .collect(Collectors.toList())
      assertEquals(1, sliders.size) //
      val timelineSlider = sliders[0] as JSlider
      timelineSlider.value = 100
      PlatformTestUtil
        .dispatchAllInvocationEventsInIdeEventQueue() // Wait for all changes in UI thread
      timelineSlider.value = 200
      PlatformTestUtil
        .dispatchAllInvocationEventsInIdeEventQueue() // Wait for all changes in UI thread
    }
  }

  @Test
  fun playbackControlActions() = runBlocking {
    val numberOfPlaybackControls = 6 // loop, go to start, play, go to end, speed, separator

    subscribeAnimations(listOf(createTransitionAnimation()))

    withContext(uiThread) {
      val toolbars =
        TreeWalker(animationPreview.component)
          .descendantStream()
          .filter { it is ActionToolbarImpl }
          .collect(Collectors.toList())
          .map { it as ActionToolbarImpl }
      val playbackControls = toolbars.firstOrNull { it.place == "Animation Preview" }
      assertNotNull(playbackControls)
      val actions = playbackControls!!.actionGroup.getChildren(null)
      assertEquals(numberOfPlaybackControls, actions.size)
      val actionEvent = Mockito.mock(AnActionEvent::class.java)
      // Press loop
      val loopAction = actions[0] as ToggleAction
      loopAction.setSelected(actionEvent, true)
      PlatformTestUtil
        .dispatchAllInvocationEventsInIdeEventQueue() // Wait for all changes in UI thread
      // Play and pause
      val playAction = actions[2]
      playAction.actionPerformed(actionEvent)
      PlatformTestUtil
        .dispatchAllInvocationEventsInIdeEventQueue() // Wait for all changes in UI thread
      playAction.actionPerformed(actionEvent)
      PlatformTestUtil
        .dispatchAllInvocationEventsInIdeEventQueue() // Wait for all changes in UI thread
      // Go to start.
      val goToStart = actions[1]
      goToStart.actionPerformed(actionEvent)
      PlatformTestUtil
        .dispatchAllInvocationEventsInIdeEventQueue() // Wait for all changes in UI thread
      // Go to end.
      val toToEnd = actions[3]
      toToEnd.actionPerformed(actionEvent)
      PlatformTestUtil
        .dispatchAllInvocationEventsInIdeEventQueue() // Wait for all changes in UI thread
      // Un-press loop
      loopAction.setSelected(actionEvent, false)
      PlatformTestUtil
        .dispatchAllInvocationEventsInIdeEventQueue() // Wait for all changes in UI thread
    }
  }

  @Test
  fun resizeInspector() = runBlocking {
    subscribeAnimations(listOf(createTransitionAnimation()))

    animationPreview.component.setSize(
      animationPreview.component.size.width * 2,
      animationPreview.component.size.height * 2,
    )
  }

  @Test
  fun animationStatesInferredForBoolean() = runBlocking {
    val transitionAnimation =
      object : ComposeAnimation {
        override val animationObject = Any()
        override val type = ComposeAnimationType.TRANSITION_ANIMATION
        override val states = setOf(true)
      }

    subscribeAnimations(listOf(transitionAnimation))

    val ui = createFakeUiForInspector(animationPreview)

    // Find the AnimationCard toolbar
    val animationCardToolbar = animationPreview.component.findToolbar("AnimationCard") as JComponent

    // Assertions
    assertEquals(5, animationCardToolbar.componentCount)
    assertEquals("true", animationCardToolbar.components[2].findComboBox().text) // From state
    assertEquals(
      "false",
      animationCardToolbar.components[4].findComboBox().text,
    ) // To state (inferred)

    withContext(uiThread) {
      // Simulate clicking the "Swap" button
      ui.clickOn(animationCardToolbar.components[1])
    }

    // Check if states have been swapped
    assertEquals("false", animationCardToolbar.components[2].findComboBox().text) // From state
    assertEquals("true", animationCardToolbar.components[4].findComboBox().text) // To state
  }

  @Test
  fun tabsAreNamedFromAnimationLabel() = runBlocking {
    val animation1 = createComposeAnimation("repeatedLabel")

    val animationWithSameLabel = createComposeAnimation("repeatedLabel")

    val animatedValueWithNullLabel =
      createComposeAnimation(type = ComposeAnimationType.ANIMATED_VALUE)

    val transitionAnimationWithNullLabel =
      createComposeAnimation(type = ComposeAnimationType.TRANSITION_ANIMATION)

    val animatedVisibilityWithNullLabel =
      createComposeAnimation(type = ComposeAnimationType.ANIMATED_VISIBILITY)

    subscribeAnimations(
      listOf(
        animation1,
        animationWithSameLabel,
        animatedValueWithNullLabel,
        transitionAnimationWithNullLabel,
        animatedVisibilityWithNullLabel,
      )
    )

    assertEquals(5, animationPreview.animations.size)
    assertEquals("repeatedLabel", animationPreview.getAnimationTitleAt(0))
    assertEquals(
      "repeatedLabel (1)",
      animationPreview.getAnimationTitleAt(1),
    ) // repeated titles get their index incremented
    assertEquals(
      "Animated Value",
      animationPreview.getAnimationTitleAt(2),
    ) // null labels use default title
    assertEquals(
      "Transition Animation",
      animationPreview.getAnimationTitleAt(3),
    ) // null labels use default title
    assertEquals(
      "Animated Visibility",
      animationPreview.getAnimationTitleAt(4),
    ) // null labels use default title
  }

  @Test
  fun `cards and timeline elements are added to coordination panel`() = runBlocking {
    subscribeAnimations(animations)

    withContext(uiThread) {
      val cards = findAllCards(animationPreview.component)
      val timeline = TestUtils.findTimeline(animationPreview.component)
      // 11 cards and 11 TimelineElements are added to coordination panel.
      assertEquals(11, cards.size)
      assertInstanceOf<AnimationCard>(cards[0])
      assertInstanceOf<LabelCard>(cards[1])
      assertInstanceOf<AnimationCard>(cards[2])
      assertInstanceOf<LabelCard>(cards[3])
      assertInstanceOf<LabelCard>(cards[4])
      assertInstanceOf<AnimationCard>(cards[5])
      assertInstanceOf<AnimationCard>(cards[6])
      assertInstanceOf<LabelCard>(cards[7])
      assertInstanceOf<AnimationCard>(cards[8])
      assertInstanceOf<LabelCard>(cards[9])
      assertInstanceOf<LabelCard>(cards[10])
      assertEquals(11, timeline.sliderUI.elements.size)
      // Only coordination tab is opened.
      assertEquals(1, animationPreview.tabbedPane.tabCount)
    }
  }

  @Test
  fun `managers are created for each animation`(): Unit = runBlocking {
    subscribeAnimations(animations)

    assertEquals(11, animationPreview.animations.size)
    assertInstanceOf<ComposeAnimationManager>(animationPreview.animations[0])
    assertInstanceOf<UnsupportedAnimationManager>(animationPreview.animations[1])
    assertInstanceOf<ComposeAnimationManager>(animationPreview.animations[2])
    assertInstanceOf<UnsupportedAnimationManager>(animationPreview.animations[3])
    assertInstanceOf<UnsupportedAnimationManager>(animationPreview.animations[4])
    assertInstanceOf<ComposeAnimationManager>(animationPreview.animations[5])
    assertInstanceOf<ComposeAnimationManager>(animationPreview.animations[6])
    assertInstanceOf<UnsupportedAnimationManager>(animationPreview.animations[7])
    assertInstanceOf<ComposeAnimationManager>(animationPreview.animations[8])
    assertInstanceOf<UnsupportedAnimationManager>(animationPreview.animations[9])
    assertInstanceOf<UnsupportedAnimationManager>(animationPreview.animations[10])
  }

  @Test
  fun invalidateInspector() = runBlocking {
    subscribeAnimations(animations)

    assertEquals(11, animationPreview.animations.size)
    assertNull(animationPreview.noAnimationsPanel())

    animationPreview.invalidatePanel().join()
    assertEquals(0, animationPreview.animations.size)
    assertNotNull(animationPreview.noAnimationsPanel())
  }

  @Test
  fun invalidateInspectorShouldClearTabsAndShowNoAnimationsPanel() = runBlocking {
    subscribeAnimations(listOf(createComposeAnimation()))

    assertNotNull(animationPreview.tabbedPane.parent)
    assertEquals(1, animationPreview.animations.size)
    assertNull(animationPreview.noAnimationsPanel())
    assertEquals(1, animationPreview.animationPreviewCardsCount())

    animationPreview.invalidatePanel().join()
    assertNotNull(animationPreview.noAnimationsPanel())
    assertNull(animationPreview.tabbedPane.parent)
    assertEquals(0, animationPreview.animations.size)
    assertEquals(0, animationPreview.animationPreviewCardsCount())
  }

  private fun ComposeAnimationPreview.getAnimationTitleAt(index: Int): String =
    findAllCards(this.component)[index].findLabel().text

  private fun ComposeAnimationPreview.noAnimationsPanel() =
    TreeWalker(this.component)
      .descendantStream()
      .filter { it.name == "Loading Animations Panel" }
      .getIfSingle()

  private fun ComposeAnimationPreview.animationPreviewCardsCount(): Int = coordinationTab.cards.size
}

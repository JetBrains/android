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
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.scene.render
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
import com.android.tools.rendering.classloading.NopClassLocator
import com.android.tools.rendering.classloading.PreviewAnimationClockMethodTransform
import com.android.tools.rendering.classloading.loaders.AsmTransformingLoader
import com.android.tools.rendering.classloading.loaders.ClassLoaderLoader
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.rendering.classloading.toClassTransform
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertInstanceOf
import com.intellij.util.containers.getIfSingle
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.io.IOException
import java.util.stream.Collectors
import javax.swing.JComponent
import javax.swing.JSlider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.createUrlClassLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito

class ComposeAnimationPreviewTest : InspectorTests() {

  private fun getClock() = TestClock()

  private val animations =
    ComposeAnimationType.values().map { createComposeAnimation(it.toString(), type = it) }

  private suspend fun subscribeAnimations(animations: List<ComposeAnimation>) {
    surface.sceneManagers.forEach { it.render() }
    val clock = getClock()
    animations.forEach { ComposeAnimationSubscriber.onAnimationSubscribed(clock, it) }
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
  fun subscribeAndUnsubscribe() = runBlocking {
    createAndOpenInspector()

    val animation = createComposeAnimation()
    assertTrue(ComposeAnimationSubscriber.hasNoAnimationsForTests())

    ComposeAnimationSubscriber.onAnimationSubscribed(getClock(), animation)
    assertFalse(ComposeAnimationSubscriber.hasNoAnimationsForTests())

    val otherAnimation = createComposeAnimation()
    ComposeAnimationSubscriber.onAnimationUnsubscribed(otherAnimation)
    assertFalse(ComposeAnimationSubscriber.hasNoAnimationsForTests())

    ComposeAnimationSubscriber.onAnimationUnsubscribed(animation)
    assertTrue(ComposeAnimationSubscriber.hasNoAnimationsForTests())
  }

  @Test
  fun closingInspectorClearsSubscriptions() = runBlocking {
    createAndOpenInspector()

    ComposeAnimationSubscriber.onAnimationSubscribed(getClock(), createComposeAnimation())
    assertFalse(ComposeAnimationSubscriber.hasNoAnimationsForTests())

    ComposeAnimationInspectorManager.closeCurrentInspector()
    assertTrue(ComposeAnimationSubscriber.hasNoAnimationsForTests())
  }

  @Test
  fun noAnimationsPanelShownWhenNoAnimationsAreSubscribed() = runBlocking {
    val inspector = createAndOpenInspector()
    UIUtil.pump() // Wait for UI to dispatch all events
    // When first opening the inspector, we show the panel informing there are no supported
    // animations to be displayed
    assertNotNull(inspector.noAnimationsPanel())
    assertNull(inspector.tabbedPane.parent)
    assertEquals(0, inspector.animations.size)

    val clock = getClock()
    // After subscribing an animation, we should display the tabbedPane
    val animation = createComposeAnimation()
    ComposeAnimationSubscriber.onAnimationSubscribed(clock, animation)
    assertNull(inspector.noAnimationsPanel())
    assertNotNull(inspector.tabbedPane.parent)
    assertEquals(1, inspector.animations.size)

    // After subscribing an animation, we should display the tabbedPane
    val anotherAnimation = createComposeAnimation()
    ComposeAnimationSubscriber.onAnimationSubscribed(clock, anotherAnimation)
    assertNull(inspector.noAnimationsPanel())
    assertNotNull(inspector.tabbedPane.parent)
    assertEquals(2, inspector.animations.size)

    // After unsubscribing from one animation, animation panel still shown.
    ComposeAnimationSubscriber.onAnimationUnsubscribed(animation)
    assertNull(inspector.noAnimationsPanel())
    assertNotNull(inspector.tabbedPane.parent)
    assertEquals(1, inspector.animations.size)

    // After unsubscribing all animations, we should hide the tabbed panel and again display the no
    // animations panel
    ComposeAnimationSubscriber.onAnimationUnsubscribed(anotherAnimation)
    assertNotNull(inspector.noAnimationsPanel())
    assertNull(inspector.tabbedPane.parent)
    assertEquals(0, inspector.animations.size)

    // After subscribing again to the animation, we should display the tabbedPane
    ComposeAnimationSubscriber.onAnimationSubscribed(clock, animation)
    assertNull(inspector.noAnimationsPanel())
    assertNotNull(inspector.tabbedPane.parent)
    assertEquals(1, inspector.animations.size)
  }

  @Test
  fun oneTabPerSubscribedAnimation() = runBlocking {
    val inspector = createAndOpenInspector()
    assertNull(inspector.tabbedPane.parent)
    assertEquals(0, inspector.animations.size)

    val animation1 = createComposeAnimation()
    val clock = getClock()
    ComposeAnimationSubscriber.onAnimationSubscribed(clock, animation1)
    assertNotNull(inspector.tabbedPane.parent)
    assertEquals(1, inspector.animations.size)

    val animation2 = createComposeAnimation()
    ComposeAnimationSubscriber.onAnimationSubscribed(clock, animation2)
    assertEquals(2, inspector.animations.size)

    ComposeAnimationSubscriber.onAnimationUnsubscribed(animation1)
    assertEquals(1, inspector.animations.size)
  }

  @Test
  fun subscriptionNewClockClearsPreviousClockAnimations() = runBlocking {
    val inspector = createAndOpenInspector()
    assertNull(inspector.tabbedPane.parent)
    assertEquals(0, inspector.animations.size)

    val clock = getClock()
    ComposeAnimationSubscriber.onAnimationSubscribed(clock, createComposeAnimation())
    assertNotNull(inspector.tabbedPane.parent)
    assertEquals(1, inspector.animations.size)

    val anotherClock = getClock()
    ComposeAnimationSubscriber.onAnimationSubscribed(anotherClock, createComposeAnimation())
    println("Animations tab: ${inspector.animations}")
    assertEquals(1, inspector.animations.size)

    ComposeAnimationSubscriber.onAnimationSubscribed(anotherClock, createComposeAnimation())
    assertEquals(2, inspector.animations.size)
  }

  @Test
  fun onOpenNewInspectorCallbackClearedWhenClosingInspector() {
    var callbackCalls = 0
    ComposeAnimationInspectorManager.createAnimationInspectorPanel(
      surface,
      parentDisposable,
      psiFilePointer,
    ) {
      callbackCalls++
    }
    ComposeAnimationInspectorManager.onAnimationInspectorOpened()
    ComposeAnimationInspectorManager.onAnimationInspectorOpened()
    assertEquals(2, callbackCalls)

    ComposeAnimationInspectorManager.closeCurrentInspector()
    ComposeAnimationInspectorManager.onAnimationInspectorOpened()
    assertEquals(2, callbackCalls)
  }

  @Test
  fun comboBoxesDisplayComposeAnimationStates() = runBlocking {
    val inspector = createAndOpenInspector()
    val animationStates = setOf(AnimationState.State1, AnimationState.State2, AnimationState.State3)
    val transitionAnimation = createTransitionAnimation(animationStates)

    subscribeAnimations(listOf(transitionAnimation))

    val ui = createFakeUiForInspector(inspector)

    // Find the AnimationCard toolbar
    val animationCardToolbar = inspector.component.findToolbar("AnimationCard") as JComponent

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
    val inspector = createAndOpenInspector()
    val animatedVisibilityAnimation =
      object : ComposeAnimation {
        override val animationObject = Any()
        override val type = ComposeAnimationType.ANIMATED_VISIBILITY
        override val states = setOf("Enter", "Exit")
      }

    subscribeAnimations(listOf(animatedVisibilityAnimation))

    val ui = createFakeUiForInspector(inspector)

    // Find the AnimationCard toolbar
    val animationCardToolbar = inspector.component.findToolbar("AnimationCard") as JComponent

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
    val inspector = createAndOpenInspector()

    subscribeAnimations(listOf(createTransitionAnimation()))

    withContext(uiThread) {
      // We can get any of the combo boxes, since "from" and "to" states should be the same.
      val sliders =
        TreeWalker(inspector.component)
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
    val inspector = createAndOpenInspector()

    subscribeAnimations(listOf(createTransitionAnimation()))

    withContext(uiThread) {
      val toolbars =
        TreeWalker(inspector.component)
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
    val inspector = createAndOpenInspector()
    subscribeAnimations(listOf(createTransitionAnimation()))

    inspector.component.setSize(
      inspector.component.size.width * 2,
      inspector.component.size.height * 2,
    )
  }

  @Test
  fun animationStatesInferredForBoolean() = runBlocking {
    val inspector = createAndOpenInspector()
    val transitionAnimation =
      object : ComposeAnimation {
        override val animationObject = Any()
        override val type = ComposeAnimationType.TRANSITION_ANIMATION
        override val states = setOf(true)
      }

    subscribeAnimations(listOf(transitionAnimation))

    val ui = createFakeUiForInspector(inspector)

    // Find the AnimationCard toolbar
    val animationCardToolbar = inspector.component.findToolbar("AnimationCard") as JComponent

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
    val inspector = createAndOpenInspector()

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

    assertEquals(5, inspector.animations.size)
    assertEquals("repeatedLabel", inspector.getAnimationTitleAt(0))
    assertEquals(
      "repeatedLabel (1)",
      inspector.getAnimationTitleAt(1),
    ) // repeated titles get their index incremented
    assertEquals(
      "Animated Value",
      inspector.getAnimationTitleAt(2),
    ) // null labels use default title
    assertEquals(
      "Transition Animation",
      inspector.getAnimationTitleAt(3),
    ) // null labels use default title
    assertEquals(
      "Animated Visibility",
      inspector.getAnimationTitleAt(4),
    ) // null labels use default title
  }

  @Test
  fun `cards and timeline elements are added to coordination panel`() = runBlocking {
    val inspector = createAndOpenInspector()
    subscribeAnimations(animations)

    withContext(uiThread) {
      val cards = findAllCards(inspector.component)
      val timeline = TestUtils.findTimeline(inspector.component)
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
      assertEquals(1, inspector.tabbedPane.tabCount)
    }
  }

  @Test
  fun `managers are created for each animation`(): Unit = runBlocking {
    val inspector = createAndOpenInspector()
    subscribeAnimations(animations)

    assertEquals(11, inspector.animations.size)
    assertInstanceOf<ComposeAnimationManager>(inspector.animations[0])
    assertInstanceOf<UnsupportedAnimationManager>(inspector.animations[1])
    assertInstanceOf<ComposeAnimationManager>(inspector.animations[2])
    assertInstanceOf<UnsupportedAnimationManager>(inspector.animations[3])
    assertInstanceOf<UnsupportedAnimationManager>(inspector.animations[4])
    assertInstanceOf<ComposeAnimationManager>(inspector.animations[5])
    assertInstanceOf<ComposeAnimationManager>(inspector.animations[6])
    assertInstanceOf<UnsupportedAnimationManager>(inspector.animations[7])
    assertInstanceOf<ComposeAnimationManager>(inspector.animations[8])
    assertInstanceOf<UnsupportedAnimationManager>(inspector.animations[9])
    assertInstanceOf<UnsupportedAnimationManager>(inspector.animations[10])
  }

  @Test
  fun invalidateInspector() {
    val psiFile =
      projectRule.fixture.addFileToProject(
        "src/main/NewTest.kt",
        """
      fun main() {}
    """
          .trimIndent(),
      )
    val anotherPsiFile =
      projectRule.fixture.addFileToProject(
        "src/main/AnotherTest.kt",
        """
      fun main() {}
    """
          .trimIndent(),
      )

    lateinit var psiPointerOne: SmartPsiElementPointer<PsiFile>
    lateinit var psiPointerTwo: SmartPsiElementPointer<PsiFile>
    lateinit var anotherPsiPointer: SmartPsiElementPointer<PsiFile>

    ApplicationManager.getApplication().invokeAndWait {
      psiPointerOne = SmartPointerManager.createPointer(psiFile)
      psiPointerTwo = SmartPointerManager.createPointer(psiFile)
      anotherPsiPointer = SmartPointerManager.createPointer(anotherPsiFile)
    }

    ComposeAnimationInspectorManager.createAnimationInspectorPanel(
      surface,
      parentDisposable,
      psiPointerOne,
    ) {}

    runBlocking {
      subscribeAnimations(animations)

      assertNotNull(ComposeAnimationInspectorManager.currentInspector)
      assertEquals(11, ComposeAnimationInspectorManager.currentInspector!!.animations.size)
      assertNull(ComposeAnimationInspectorManager.currentInspector!!.noAnimationsPanel())

      ComposeAnimationInspectorManager.invalidate(anotherPsiPointer).join()
      assertNotNull(ComposeAnimationInspectorManager.currentInspector)
      assertEquals(11, ComposeAnimationInspectorManager.currentInspector!!.animations.size)
      assertNull(ComposeAnimationInspectorManager.currentInspector!!.noAnimationsPanel())

      ComposeAnimationInspectorManager.invalidate(psiPointerTwo).join()
      assertNotNull(ComposeAnimationInspectorManager.currentInspector)
      assertEquals(0, ComposeAnimationInspectorManager.currentInspector!!.animations.size)
      assertNotNull(ComposeAnimationInspectorManager.currentInspector!!.noAnimationsPanel())
    }
  }

  @Test
  fun invalidateInspectorShouldClearTabsAndShowNoAnimationsPanel() = runBlocking {
    val inspector = createAndOpenInspector()
    subscribeAnimations(listOf(createComposeAnimation()))

    assertNotNull(inspector.tabbedPane.parent)
    assertEquals(1, inspector.animations.size)
    assertNull(inspector.noAnimationsPanel())
    assertEquals(1, inspector.animationPreviewCardsCount())

    ComposeAnimationInspectorManager.invalidate(psiFilePointer).join()
    assertNotNull(inspector.noAnimationsPanel())
    assertNull(inspector.tabbedPane.parent)
    assertEquals(0, inspector.animations.size)
    assertEquals(0, inspector.animationPreviewCardsCount())
  }

  @Test
  @Throws(IOException::class, ClassNotFoundException::class)
  fun classLoaderRedirectsSubscriptionToAnimationManager() {
    class PreviewAnimationClockClassLoader :
      DelegatingClassLoader(
        this.javaClass.classLoader,
        AsmTransformingLoader(
          toClassTransform({ PreviewAnimationClockMethodTransform(it) }),
          ClassLoaderLoader(
            createUrlClassLoader(
              listOf(
                resolveWorkspacePath("tools/adt/idea/compose-designer/testData/classloader")
                  .resolve("composeanimation.jar")
              )
            )
          ),
          NopClassLocator,
        ),
      ) {
      fun loadPreviewAnimationClock(): Class<*> =
        loadClass("androidx.compose.ui.tooling.animation.PreviewAnimationClock")
    }
    createAndOpenInspector()

    val previewAnimationClockClassLoader = PreviewAnimationClockClassLoader()
    val previewAnimationClock = previewAnimationClockClassLoader.loadPreviewAnimationClock()
    val notifySubscribe =
      previewAnimationClock.getDeclaredMethod("notifySubscribe", ComposeAnimation::class.java)
    val animation = createComposeAnimation()
    notifySubscribe.invoke(previewAnimationClock.newInstance(), animation)
    assertFalse(ComposeAnimationSubscriber.hasNoAnimationsForTests())

    val notifyUnsubscribe =
      previewAnimationClock.getDeclaredMethod("notifyUnsubscribe", ComposeAnimation::class.java)
    notifyUnsubscribe.invoke(previewAnimationClock.newInstance(), animation)
    assertTrue(ComposeAnimationSubscriber.hasNoAnimationsForTests())
  }

  @Test
  fun animationClockWrapsComposeClockViaReflection() {
    val animationClock = AnimationClock(getClock())
    // Check that we can find a couple of methods from TestClock
    assertNotNull(animationClock.getAnimatedPropertiesFunction)
    assertNotNull(animationClock.updateAnimatedVisibilityStateFunction)
    // getAnimatedVisibilityState is mangled in TestClock, but we should be able to find it.
    assertNotNull(animationClock.getAnimatedVisibilityStateFunction)

    try {
      // We should throw an Exception if we can't find the given function in the underlying clock,
      // and it's up to the caller to handle this.
      animationClock.findClockFunction("unknownFunction")
      fail("Expected to fail, as `unknownFunction` is not a function of TestClock.")
    } catch (ignored: NullPointerException) {}

    // getAnimatedVisibilityState is a supported function, but its name is mangled. We should find
    // it when looking for the function without
    // the hash suffix, not when we specify it.
    assertNotNull(animationClock.findClockFunction("getAnimatedVisibilityState"))
    try {
      animationClock.findClockFunction("getAnimatedVisibilityState-xga21d")
      fail(
        "Expected to fail, as `getAnimatedVisibilityState-xga21d` should not be found when looking for the mangled name."
      )
    } catch (ignored: NullPointerException) {}
  }

  @Test
  fun inspectorShouldBeClosedWhenParentIsDisposed() {
    val disposable = Disposer.newDisposable()
    createAndOpenInspector(disposable)
    assertTrue(ComposeAnimationInspectorManager.isInspectorOpen())
    Disposer.dispose(disposable)
    assertFalse(ComposeAnimationInspectorManager.isInspectorOpen())
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
